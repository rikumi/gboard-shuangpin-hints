/* SPDX-License-Identifier: LGPL-2.1-or-later */
package io.github.rikumi.gboardhints;
import java.io.*;
import java.util.zip.*;
final class ModuleAssets {
    static InputStream open(String name) throws IOException {
        String apk=io.github.rikumi.gboardhints.bridge.XposedBridge.moduleApk;
        if(apk==null)throw new FileNotFoundException("Module path unavailable");
        ZipFile zip=new ZipFile(apk);
        ZipEntry entry=zip.getEntry("assets/"+name);
        if(entry==null){zip.close();throw new FileNotFoundException("Module asset unavailable");}
        try {
            return new FilterInputStream(zip.getInputStream(entry)) {
                @Override public void close() throws IOException {try{super.close();}finally{zip.close();}}
            };
        }catch(IOException error){zip.close();throw error;}
    }
}
