package edu.ucsd.arcum.util;

import java.io.*;
import java.net.URL;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.ui.internal.util.BundleUtility;
import org.osgi.framework.Bundle;

import edu.ucsd.arcum.ArcumPlugin;
import edu.ucsd.arcum.exceptions.ArcumError;

public class FileUtil
{
    public static String readBundledFile(String filePath) {
        Bundle bundle = ArcumPlugin.getDefault().getBundle();
        if (!BundleUtility.isReady(bundle)) {
            return null;
        }
        try {
            URL url = BundleUtility.find(bundle, filePath);
            if (url == null) {
                url = new URL(filePath);
            }
            return FileUtil.readStream(url.openStream());
        }
        catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public static String readFile(IFile file) {
        if (!file.exists())
            return "";
        InputStream is = null;
        try {
            is = file.getContents();
            return FileUtil.readStream(is);
        }
        catch (RuntimeException e) {
            throw e;
        }
        catch (Exception e) {
            ArcumError.fatalUserError(null, "%s: %s", e.getClass().getCanonicalName(),
                    e.getMessage());
        }
        finally {
            try {
                if (is != null)
                    is.close();
            }
            catch (IOException e) {
                return "";
            }
        }
        return "";
    }
    
    public static String readStream(InputStream is) throws IOException {
        Reader in = new BufferedReader(new InputStreamReader(is));
        StringBuilder result = new StringBuilder(2048);
        char[] buff = new char[2048];
        for (;;) {
            int c = in.read(buff);
            if (c <= 0)
                break;
            result.append(buff, 0, c);
        }
        return result.toString();
    }
    
    public static String readFileWithThrow(IFile in) throws CoreException, IOException {
        InputStream contents = in.getContents();
        return readStream(contents);
    }
}
