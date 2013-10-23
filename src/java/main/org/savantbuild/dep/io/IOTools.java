/*
 * Copyright (c) 2001-2010, Inversoft, All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */
package org.savantbuild.dep.io;

import org.savantbuild.dep.util.StringTools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * This class provides some common IO tools.
 *
 * @author Brian Pontarelli
 */
public class IOTools {
  /**
   * Parses an MD5 from the given Path.
   *
   * @param path The path to parse the MD5 sum from.
   * @return The MD5.
   */
  public static MD5 parseMD5(Path path) {
    if (path == null || !Files.isRegularFile(path)) {
      return null;
    }

    return protectIO(() -> {
      String str = new String(Files.readAllBytes(path), "UTF-8");
      String name = null;
      String sum = null;
      if (str.length() > 0) {
        // Validate format (should be either only the md5sum or the sum plus the file name)
        if (str.length() < 32) {
          throw new MD5Exception("Invalid md5sum [" + str + "]");
        }

        if (str.length() == 32) {
          sum = str;
        } else if (str.length() > 33) {
          int index = str.indexOf(" ");
          if (index == 32) {
            sum = str.substring(0, 32);

            // Find file name and verify
            while (str.charAt(index) == ' ') {
              index++;
            }

            if (index == str.length()) {
              throw new MD5Exception("Invalid md5sum [" + str + "]");
            }

            name = str.substring(index);
          } else {
            throw new MD5Exception("Invalid md5sum [" + str + "]");
          }
        } else {
          throw new MD5Exception("Invalid md5sum [" + str + "]");
        }
      }

      return new MD5(sum, StringTools.fromHex(sum), name);
    });
  }

  /**
   * Runs the {@link IOCallable} in a protected state. This method handles many of the IOException subclasses and
   * translates them into either a {@link TemporaryIOException}, {@link PermanentIOException} or {@link
   * DoesNotExistException}. This does not handle any of the RMI exceptions.
   * <p/>
   * Most of the IOExceptions are translated into TemporaryIOExceptions because they are transient and will likely not
   * occur under different conditions. However, there are a couple of IOExceptions that are permanent. These are
   * MD5Exception and MalformedURLException. There are also two DoesNotExistExceptions: FileNotFoundException and
   * NoSuchFileException.
   *
   * @param callable The IO callable to execute.
   * @return The result of the Callable.
   * @throws PermanentIOException  If the IO caused an exception and it was not recoverable. This means that calling
   *                               this method with the same Callable will always cause the error and it will never
   *                               succeed.
   * @throws TemporaryIOException  If the IO caused an exception and it was only temporary due to environmental
   *                               conditions or other causes that will eventually be remedied.
   * @throws DoesNotExistException If the IO is doing file IO and the file does not exist.
   */
  public static <T> T protectIO(IOCallable<T> callable)
      throws PermanentIOException, TemporaryIOException, DoesNotExistException {
    try {
      return callable.call();
    } catch (FileNotFoundException | NoSuchFileException e) {
      throw new DoesNotExistException(e);
    } catch (IOException e) {
      throw new TemporaryIOException(e);
    }
  }

  /**
   * Reads from the given input stream and writes the contents out to the given OutputStream. During the write, the MD5
   * sum from input stream is calculated and compared with the given MD5 sum. This does not close the InputStream but
   * <b>DOES</b> close the OutputStream so that the data gets flushed out correctly.
   *
   * @param is  The InputStream to read from. This InputStream is wrapped in a BufferedInputStream for performance.
   * @param os  The OutputStream to write to.
   * @param md5 (Optional) The MD5 sum to check against.
   * @return The MD5 checksum of the file that was written out. This helps in case the caller needs the sum and the
   *         parameter is not given.
   * @throws IOException  If the output operation fails.
   * @throws MD5Exception If the MD5 check failed.
   */
  public static MD5 write(InputStream is, OutputStream os, MD5 md5) throws IOException {
    MessageDigest digest;
    // Copy to the file can do the MD5 sum while copying
    try {
      digest = MessageDigest.getInstance("MD5");
      digest.reset();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Unable to locate MD5 algorithm");
    }

    DigestInputStream inputStream = new DigestInputStream(new BufferedInputStream(is), digest);
    inputStream.on(true);

    try (BufferedOutputStream bof = new BufferedOutputStream(os)) {
      // Then output the file
      byte[] b = new byte[8192];
      int len;
      while ((len = inputStream.read(b)) != -1) {
        bof.write(b, 0, len);
      }
    }

    if (md5 != null && md5.bytes != null) {
      byte[] localMD5 = digest.digest();
      if (localMD5 != null && !Arrays.equals(localMD5, md5.bytes)) {
        throw new MD5Exception("MD5 mismatch when writing from the InputStream to the OutputStream.");
      }
    }

    byte[] bytes = inputStream.getMessageDigest().digest();
    return new MD5(StringTools.toHex(bytes), bytes, null);
  }

  public static interface IOCallable<T> {
    T call() throws IOException;
  }
}
