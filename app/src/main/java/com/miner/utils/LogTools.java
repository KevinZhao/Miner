package com.miner.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by Mr.Zhang on 2018/2/26.
 */

public class LogTools {
    public static String ms2Date(long ms){
        Date date=new Date(ms);
        SimpleDateFormat format=new SimpleDateFormat("yyyyMMddHHmmss");
        return format.format(date);
    }

    /**
     * 获取指定文件大小(单位：MB)
     *
     * @param file
     * @return
     * @throws Exception
     *
     */
    public static double getFileSize(File file) {
        if (file == null) {
            return 0;
        }
        long size = 0;
        if (file.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                size = fis.available();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
        return size/1024/1024;
    }

    public static File createFile(Context mContext, String folder,String filename){
        String s = mContext.getExternalFilesDir(folder).getAbsolutePath();
        File saveFile = new File(s, filename);
        return saveFile;
    }

    public static void write2File(File saveFile, String writecontent){
        FileOutputStream outStream = null;
        try {
            outStream = new FileOutputStream(saveFile,true);
            outStream.write(writecontent.getBytes());
            outStream.flush();
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
