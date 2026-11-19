# JPEG·PNG·WebP의 픽셀을 다시 인코딩해 원본 개인 메타데이터를 제거한다.
import io
import sys
import warnings

from PIL import Image, ImageOps


def sanitize(data, expected_format, max_pixels):
    Image.MAX_IMAGE_PIXELS = max_pixels
    warnings.simplefilter("error", Image.DecompressionBombWarning)
    with Image.open(io.BytesIO(data), formats=[expected_format]) as source:
        frame_count = source.n_frames if getattr(source, "is_animated", False) else 1
        if frame_count > 200 or source.width * source.height * frame_count > max_pixels * 2:
            raise ValueError("image limits")
        loop = source.info.get("loop", 0)
        default_image = expected_format == "PNG" and source.info.get("default_image", False)
        frames, durations = [], []
        for frame_index in range(frame_count):
            source.seek(frame_index)
            source.load()
            frame = ImageOps.exif_transpose(source).convert("RGB" if expected_format == "JPEG" else "RGBA")
            frame.info.clear()
            frames.append(frame)
            if not default_image or frame_index != 0:
                durations.append(round(source.info.get("duration", 0)))

    options = {"quality": 92} if expected_format == "JPEG" else {}
    if expected_format == "WEBP":
        options.update(lossless=True, method=3)
    if frame_count > 1:
        options.update(save_all=True, append_images=frames[1:], duration=durations, loop=loop)
        if expected_format == "PNG":
            options.update(default_image=default_image, blend=0)
    result = io.BytesIO()
    frames[0].save(result, format=expected_format, **options)
    if result.tell() > 32 * 1024 * 1024:
        raise ValueError("output size")
    return result.getbuffer()


if __name__ == "__main__":
    try:
        payload = sys.stdin.buffer.read(10 * 1024 * 1024 + 1)
        if len(payload) > 10 * 1024 * 1024:
            raise ValueError("input size")
        cleaned = sanitize(payload, sys.argv[1], int(sys.argv[2]))
    except (OSError, SyntaxError, EOFError, ValueError, Image.DecompressionBombError, Image.DecompressionBombWarning):
        sys.exit(2)
    sys.stdout.buffer.write(cleaned)
