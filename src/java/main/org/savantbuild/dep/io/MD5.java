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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * <p>
 * This class is a simple holder for a MD5 checksum. It holds the sum and the file name.
 * It can also hold the MD5 sum bytes.
 * </p>
 *
 * @author Brian Pontarelli
 */
public class MD5 {
  public final String sum;
  public final byte[] bytes;
  public final String fileName;

  public MD5(String sum, byte[] bytes, String fileName) {
    this.sum = sum;
    this.bytes = bytes;
    this.fileName = fileName;
  }

  /**
   * Calculates the MD5 for the given bytes. This optionally takes a file name, which isn't required, but can be useful
   * when calculating MD5s for files.
   *
   * @param bytes The bytes.
   * @param fileName (Optional) The file name.
   * @return The MD5 and never null.
   * @throws IOException If the MD5 fails for any reason.
   */
  public static MD5 fromBytes(byte[] bytes, String fileName) throws IOException {
    MessageDigest digest = null;
    try {
      digest = MessageDigest.getInstance("MD5");
      digest.reset();
    } catch (NoSuchAlgorithmException e) {
      System.err.println("Unable to locate MD5 algorithm");
      System.exit(1);
    }

    // Read in the file in blocks while doing the MD5 sum
    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
    DigestInputStream dis = new DigestInputStream(bais, digest);

    byte[] ba = new byte[1024];
    while (dis.read(ba, 0, 1024) != -1) ;

    dis.close();

    byte[] md5 = digest.digest();
    return new MD5(StringTools.toHex(md5), md5, fileName);
  }

  /**
   * Writes the MD5 information out to the given Path file.
   *
   * @param md5 The MD5.
   * @param path The path to write the MD5 to.
   * @throws IOException If the write fails.
   */
  public static void writeMD5(MD5 md5, Path path) throws IOException {
    Files.write(path, md5.sum.getBytes("UTF-8"));
  }
}
