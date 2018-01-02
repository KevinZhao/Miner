package com.miner.socket;


import android.util.Log;
import android.util.Xml;

import com.miner.listener.OnSocketStateListener;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Mr.Zhang on 2017/12/18.
 */

public class SocketClient {
    static Socket client;
    OnSocketStateListener listener;
    private int port;
    private String TAG = "SocketClient";
    /*连接线程*/
    private Thread connectThread;
    private Timer timer = new Timer();
    private OutputStream outputStream;

    private String host;
    private TimerTask task;
    /*默认重连*/
    private boolean isReConnect = true;
    private InputStream inputStream;
    private BufferedWriter bufferedWriter;
    private int reconnect = 0;
    private String savePath;
    private FileOutputStream fout;


    public SocketClient(String shost, int sport, String savePath, OnSocketStateListener listener) {
        this.listener = listener;
        this.savePath = savePath;
        host = shost;
        port = sport;
        conn();
    }

    public void onDestroy() {
        isReConnect = false;
        releaseSocket();
    }

    private void conn() {
        if (client == null && connectThread == null) {
            connectThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    client = new Socket();
                    //设置连接超时
                    try {
                        client.connect(new InetSocketAddress(host, port), 5000);
                        if (client.isConnected()) {
                           /*发送心跳数据*/
                            sendBeatData();
                            //                            receiveData();
                            listener.socketstate("Connected");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        listener.socketstate("Disconnected");
                        releaseSocket();
                    }
                }
            });
              /*启动连接线程*/
            connectThread.start();
        }
    }

    private void sendBeatData() {
        if (timer == null) {
            timer = new Timer();
        }

        if (task == null) {
            task = new TimerTask() {
                @Override
                public void run() {
                    try {
                        outputStream = client.getOutputStream();
                        bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
                        bufferedWriter.write("");
                        bufferedWriter.flush();
                        Log.e(TAG, "run: " + 2);
                    } catch (Exception e) {
                        Log.e(TAG, "run: " + 3);
                        /*发送失败说明socket断开了或者出现了其他错误*/
                        listener.socketstate("Disconnected");
                        /*重连*/
                        releaseSocket();
                        reconnect++;
                        e.printStackTrace();


                    }
                }
            };
        }

        timer.schedule(task, 0, waiting() * 1000);
    }

    private int waiting() {
        if (reconnect > 20) {
            return 600;
        }

        return reconnect <= 7 ? 2 : 10;
    }

    /*发送数据*/
    public void sendOrder(final String order) {
        if (client != null && client.isConnected()) {
            /*发送指令*/
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        outputStream = client.getOutputStream();
                        if (outputStream != null) {
                            bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
                            bufferedWriter.write(order);
                            bufferedWriter.flush();
                            receiveData();
                            listener.socketstate("Transmission Completion");

                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                }
            }).start();

        } else {
            listener.socketstate("Disconnected");
            releaseSocket();
            reconnect++;
        }
    }

    /**
     * 接收数据
     */
    public void receiveData() {
        if (client != null && client.isConnected()) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        inputStream = client.getInputStream();
                        File file = new File(savePath + "/test.log");
                        fout = new FileOutputStream(file);
                        int len = 0;
                        if (inputStream != null) {
                            //                            bufferedReader=new BufferedReader(new InputStreamReader(inputStream));
                            // 客户端接收服务器端的响应，读取服务器端向客户端的输入流
                            // 缓冲区
                            while (true) {
                                byte[] buffer = new byte[inputStream.available()];
                                // 读取缓冲区
                                len = inputStream.read(buffer);
                                if (len == -1) break;
                                fout.write(buffer, 0, len);
                                fout.flush();
                            }
                            fout.close();
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } else {
            listener.socketstate("Disconnected");
            releaseSocket();
        }
    }

    /*释放资源*/
    private void releaseSocket() {

        if (task != null) {
            task.cancel();
            task = null;
        }
        if (timer != null) {
            timer.purge();
            timer.cancel();
            timer = null;
        }

        if (outputStream != null) {
            try {
                outputStream.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
            outputStream = null;
        }
        if (inputStream != null) {
            try {
                inputStream.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
            inputStream = null;
        }
        if (client != null) {
            try {
                client.close();

            } catch (IOException e) {
            }
            client = null;
        }

        if (connectThread != null) {
            connectThread = null;
        }

          /*重新初始化socket*/
        if (isReConnect) {
            conn();
        }

    }

    //    /**
    //     * 服务器是否关闭，通过发送一个socket信息
    //     *
    //     * @return
    //     */
    //    public boolean canConnectToServer() {
    //        try {
    //            if (client != null) {
    //                client.sendUrgentData(0xff);
    //            }
    //        } catch (IOException e) {
    //            // TODO Auto-generated catch block
    //            e.printStackTrace();
    //            return false;
    //        } catch (Exception e) {
    //            e.printStackTrace();
    //            return false;
    //        }
    //        return true;
    //    }

    //    public Socket getClient() {
    //        return client;
    //    }

    //    public String sendMsg(String msg) {
    //            try {
    //                message = msg;
    //                //System.out.println(msg);
    //                listener.socketstate("Transmission");
    //                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
    //                PrintWriter out = new PrintWriter(client.getOutputStream());
    //                out.println(msg);
    //                out.flush();
    //                listener.socketstate("Transmission Completion");
    //                return in.readLine();
    //            } catch (IOException e) {
    //                e.printStackTrace();
    //                listener.socketstate("Transmission abnormal" + e.toString());
    //                //            conn();
    //                sendMsg(message);
    //                Log.e(TAG, "sendMsg: " + message);
    //            }
    //            return "";
    //        }


    //    public void closeSocket() {
    //        try {
    //            listener.socketstate("Socket is closed");
    //            client.close();
    //            client = null;
    //        } catch (IOException e) {
    //            e.printStackTrace();
    //        }
    //    }

}
