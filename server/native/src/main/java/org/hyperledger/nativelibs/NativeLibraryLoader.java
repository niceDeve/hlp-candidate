/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hyperledger.nativelibs;

import com.sun.jna.Native;
import org.scijava.nativelib.DefaultJniExtractor;
import org.scijava.nativelib.JniExtractor;

import java.io.File;
import java.net.URL;

public class NativeLibraryLoader {

    @SuppressWarnings("unchecked")
    public static <T> T loadLibrary(String fileName, Class<T> clazz) {
        //first try if the library is on the configured library path
        try {
            return (T) Native.loadLibrary(fileName, clazz);
        } catch (Exception | UnsatisfiedLinkError e) {
        }
        final String mapped = System.mapLibraryName(fileName);
        final String[] aols = getAOLs();
        final ClassLoader loader = NativeLibraryLoader.class.getClassLoader();
        final File unpacked = getUnpackedLibPath(loader, aols, fileName, mapped);
        if (unpacked != null) {
            return (T) Native.loadLibrary(unpacked.getPath(), clazz);
        } else try {
            final String libPath = getLibPath(loader, aols, mapped);
            final JniExtractor extractor = new DefaultJniExtractor(NativeLibraryLoader.class, System.getProperty("java.io.tmpdir"));
            final File extracted = extractor.extractJni(libPath, fileName);
            return (T) Native.loadLibrary(extracted.getPath(), clazz);
        } catch (final Exception e) {
            e.printStackTrace();
            throw e instanceof RuntimeException ?
                    (RuntimeException) e : new RuntimeException(e);
        }
    }

    private static String[] getAOLs() {
        final String ao = System.getProperty("os.arch") + "-" + System.getProperty("os.name").replaceAll(" ", "");

        // choose the list of known AOLs for the current platform
        if (ao.startsWith("i386-Linux")) {
            return new String[]{
                    "i386-Linux-ecpc", "i386-Linux-gpp", "i386-Linux-icc", "i386-Linux-ecc", "i386-Linux-icpc", "i386-Linux-linker", "i386-Linux-gcc"
            };
        } else if (ao.startsWith("x86-Windows")) {
            return new String[]{
                    "x86-Windows-linker", "x86-Windows-gpp", "x86-Windows-msvc", "x86-Windows-icl", "x86-Windows-gcc"
            };
        } else if (ao.startsWith("amd64-Linux")) {
            return new String[]{
                    "amd64-Linux-gpp", "amd64-Linux-icpc", "amd64-Linux-gcc", "amd64-Linux-linker"
            };
        } else if (ao.startsWith("amd64-Windows")) {
            return new String[]{
                    "amd64-Windows-gpp", "amd64-Windows-msvc", "amd64-Windows-icl", "amd64-Windows-linker", "amd64-Windows-gcc"
            };
        } else if (ao.startsWith("amd64-FreeBSD")) {
            return new String[]{
                    "amd64-FreeBSD-gpp", "amd64-FreeBSD-gcc", "amd64-FreeBSD-linker"
            };
        } else if (ao.startsWith("ppc-MacOSX")) {
            return new String[]{
                    "ppc-MacOSX-gpp", "ppc-MacOSX-linker", "ppc-MacOSX-gcc"
            };
        } else if (ao.startsWith("x86_64-MacOSX")) {
            return new String[]{
                    "x86_64-MacOSX-icc", "x86_64-MacOSX-icpc", "x86_64-MacOSX-gpp", "x86_64-MacOSX-linker", "x86_64-MacOSX-gcc"
            };
        } else if (ao.startsWith("ppc-AIX")) {
            return new String[]{
                    "ppc-AIX-gpp", "ppc-AIX-xlC", "ppc-AIX-gcc", "ppc-AIX-linker"
            };
        } else if (ao.startsWith("i386-FreeBSD")) {
            return new String[]{
                    "i386-FreeBSD-gpp", "i386-FreeBSD-gcc", "i386-FreeBSD-linker"
            };
        } else if (ao.startsWith("sparc-SunOS")) {
            return new String[]{
                    "sparc-SunOS-cc", "sparc-SunOS-CC", "sparc-SunOS-linker"
            };
        } else if (ao.startsWith("arm-Linux")) {
            return new String[]{
                    "arm-Linux-gpp", "arm-Linux-linker", "arm-Linux-gcc"
            };
        } else if (ao.startsWith("x86-SunOS")) {
            return new String[]{
                    "x86-SunOS-g++", "x86-SunOS-linker"
            };
        } else if (ao.startsWith("i386-MacOSX")) {
            return new String[]{
                    "i386-MacOSX-gpp", "i386-MacOSX-gcc", "i386-MacOSX-linker"
            };
        } else {
            throw new RuntimeException("Unhandled architecture/OS: " + ao);
        }
    }

    private static File getUnpackedLibPath(final ClassLoader loader, final String[] aols, final String fileName, final String mapped) {
        final String classPath = NativeLibraryLoader.class.getName().replace('.', '/') + ".class";
        final URL url = loader.getResource(classPath);
        if (url == null || !"file".equals(url.getProtocol())) return null;
        final String path = url.getPath();
        final String prefix = path.substring(0, path.length() - classPath.length());
        for (final String aol : aols) {
            final File file = new File(prefix + aol + "/lib/" + mapped);
            if (file.isFile()) return file;
        }
        return null;
    }

    private static String getLibPath(final ClassLoader loader, final String[] aols, final String mapped) {
        for (final String aol : aols) {
            final String libPath = aol + "/lib/";
            if (loader.getResource(libPath + mapped) != null) return libPath;
        }
        throw new RuntimeException("Library '" + mapped + "' not found!");
    }
}
