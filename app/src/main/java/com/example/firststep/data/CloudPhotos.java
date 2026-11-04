package com.example.firststep.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Bounded JPEG transport; server paths never become local filesystem paths. */
final class CloudPhotos {
  private static final int MAX_BYTES = 2 * 1024 * 1024;
  private CloudPhotos() {}

  static File privateFile(Context context, String path) throws IOException {
    if (path == null) throw new IOException("Missing photo");
    File root = context.getFilesDir().getCanonicalFile();
    File file = new File(path).getCanonicalFile();
    if (!root.equals(file.getParentFile()) || !file.isFile()) throw new IOException("Invalid photo");
    return file;
  }

  static String encode(Context context, String path) throws IOException {
    File file = privateFile(context, path);
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getPath(), options);
    if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Unreadable photo");
    int sample = 1;
    while (options.outWidth / sample > 1600 || options.outHeight / sample > 1600) sample *= 2;
    options.inSampleSize = sample; options.inJustDecodeBounds = false;
    Bitmap image = BitmapFactory.decodeFile(file.getPath(), options);
    if (image == null) throw new IOException("Unreadable photo");
    try {
      Matrix transform = new Matrix();
      int orientation = new ExifInterface(file.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
      switch (orientation) {
        case 2: transform.postScale(-1, 1); break;
        case 3: transform.postRotate(180); break;
        case 4: transform.postScale(1, -1); break;
        case 5: transform.postRotate(90); transform.postScale(-1, 1); break;
        case 6: transform.postRotate(90); break;
        case 7: transform.postRotate(-90); transform.postScale(-1, 1); break;
        case 8: transform.postRotate(-90); break;
        default: break;
      }
      if (!transform.isIdentity()) {
        Bitmap rotated = Bitmap.createBitmap(image, 0, 0, image.getWidth(), image.getHeight(), transform, true);
        if (rotated != image) { image.recycle(); image = rotated; }
      }
      for (int quality : new int[] {88, 70, 50}) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!image.compress(Bitmap.CompressFormat.JPEG, quality, output)) throw new IOException("Cannot encode photo");
        if (output.size() <= MAX_BYTES) return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
      }
      throw new IOException("Photo too large");
    } finally { image.recycle(); }
  }

  static String save(Context context, String namespace, String encoded) throws IOException {
    if (encoded == null || encoded.length() > ((MAX_BYTES + 2) / 3) * 4) throw new IOException("Photo too large");
    byte[] bytes;
    try { bytes = Base64.decode(encoded, Base64.NO_WRAP); }
    catch (IllegalArgumentException error) { throw new IOException("Invalid photo", error); }
    if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Invalid photo size");
    BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth > 8000 || bounds.outHeight > 8000
        || (long) bounds.outWidth * bounds.outHeight > 24000000) throw new IOException("Invalid image dimensions");
    File file = new File(context.getFilesDir(), "account_" + namespace.substring(0, 12) + "_" + java.util.UUID.randomUUID() + ".jpg");
    try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
    catch (IOException error) { file.delete(); throw error; }
    return file.getAbsolutePath();
  }
}
