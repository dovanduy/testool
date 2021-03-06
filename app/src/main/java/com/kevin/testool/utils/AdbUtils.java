package com.kevin.testool.utils;

import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import com.kevin.testool.adblib.CmdTools;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * AdbUtil 工具类
 * Created by gsx on 2016/11/8.
 */

public class AdbUtils {
    static String TAG = "AdbUtils";
    public static final String PORT = "5555"; //无线adb使用端口
    private static Boolean rooted = null;

    /**
     * 设置为空字符串""或者"/system/bin/"
     */
    private static final String EXE_PREFIX = "";

    /**
     * shell执行ps判断指定Process是否在运行
     *
     * @param processName 进程名字
     * @return 执行进程是否在运行
     * @throws
     */
    public static boolean isProcessRunning(String processName) throws Exception {
        boolean running = false;

        String res = runShellCommand("ps -A |grep " + processName, 0);

        if (res.contains(processName)) {
            running = true;
        }
        return running;
    }

    /**
     * shell执行su判断是否有root权限
     *
     * @return 当前app是否有root权限
     */
    public static boolean hasRootPermission() {
        Process process = null;
        DataOutputStream os = null;
        if (rooted != null){
            logUtil.d(TAG, "hasRootPermission: " + rooted);
            return rooted;
        }
        try {
            process = Runtime.getRuntime().exec(EXE_PREFIX+"su");
//            process = Runtime.getRuntime().exec("echo hello");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
//            logUtil.d("", process.exitValue()+"");
            rooted = process.exitValue() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            rooted = false;
        } finally {
            if (os != null) {
                try {
                    os.close();
                    process.destroy();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        logUtil.d(TAG, "hasRootPermission: " + rooted);
        return rooted;
    }

    /**
     * 是否可以执行adb命令
     * @return boolean
     */
    public static boolean isAdbEnable(){
        final boolean[] result = new boolean[1];
        if (hasRootPermission()){
            return true;
        } else {
         Thread isAdb = new Thread(new Runnable() {
             @Override
             public void run() {
                 result[0] = CmdTools.generateConnection();
             }
         });
         isAdb.start();
         while (isAdb.isAlive()){
             SystemClock.sleep(100);
         }
         return result[0];
        }
    }

    /**
     * 执行shell命令，无root权限需开启adb tcpip 5555
     * @param command 待执行的shell命令
     * @param timeout timeout == 0 阻塞命令直到执行结束;timeout>0 超时时间,超时后结束进程; timeout< 0 等待timeout绝对值时间，不返回命令结果
     * @return shell命令执行结果
     */
    public static String runShellCommand(String command, int timeout) {
        boolean waitResult = true;
        Process process = null;
        DataOutputStream os = null;
        StringBuilder successMsg = new StringBuilder();
        StringBuilder errorMsg = new StringBuilder();
        if (!hasRootPermission()){
            if (command.endsWith("\n")){
                command = command.split("\n")[0];
            }

            return CmdTools.execAdbCmd(command, timeout);
        } else {
            try {
                process = Runtime.getRuntime().exec(EXE_PREFIX + "su");
                os = new DataOutputStream(process.getOutputStream());
                if (!command.endsWith("\n")) {
                    command = command + "\n";
                }
                os.write(command.getBytes());
                os.writeChars("exit\n");
                os.flush();

                if (timeout > 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        process.waitFor(timeout, TimeUnit.MILLISECONDS);
                    } else {
                        SystemClock.sleep(timeout);
                    }
                } else if (timeout == 0) {
                    process.waitFor();
                } else {
                    waitResult = false;
                    SystemClock.sleep(Math.abs(timeout));

                }

                //输出结果
                if (waitResult) {
                    BufferedReader successResult = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));
                    BufferedReader errorResult = new BufferedReader(
                            new InputStreamReader(process.getErrorStream()));
                    String s;
                    while ((s = successResult.readLine()) != null) {
                        successMsg.append(s);
                    }
                    while ((s = errorResult.readLine()) != null) {
                        errorMsg.append(s);
                        Log.i(TAG, errorMsg.toString());
                    }
                }

            } catch (Exception e) {
                logUtil.e("AdbUtils.runShellCommand", e.getMessage());
                return "-1";
            } finally {
                try {
                    if (os != null) {
                        os.close();
                    }
                    process.destroy();
                } catch (Exception e) {
                    logUtil.e("AdbUtils.runShellCommand", e.getMessage());
                }
            }
        }
//        logUtil.d("debug", successMsg.toString());
        return successMsg.toString();
    }

    /**
     * 设置系统属性值，这些属性值可以通过getprop获取，setprop设置
     *
     * @param property 属性名
     * @param value    属性值
     * @return 是否成功设置属性值
     */
    public static String setProp(String property, String value) {
        return runShellCommand("setprop " + property + " " + value, 0);
    }

    /**
     * 启动adb服务
     *
     * @return 是否成功启动adb服务
     */
    public static boolean adbStart() {
        try {
            runShellCommand("stop adbd", 0);
            setProp("service.adb.tcp.port", PORT);
            runShellCommand("stop adbd", 0);
            runShellCommand("start adbd", 0);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 停止adb服务
     *
     * @return 是否成功停止adb服务
     */
    public static boolean adbStop() {
        try {
            setProp("service.adb.tcp.port", "-1");
            runShellCommand("stop adbd", 0);
            runShellCommand("start adbd", 0);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;

    }

    /**
     * 检测adb服务是否正在运行
     *
     * @return 是否正在运行adb服务
     */
    public static boolean adbIfRunning() {
        final boolean[] running = {false};
        new Thread(){
            @Override
            public void run() {

                String res = runShellCommand("getprop service.adb.tcp.port", 0);
                System.out.println(res);
                if (res.contains(PORT)) {
                    running[0] = true;
                }
            }
        }.start();
        SystemClock.sleep(200);
        return running[0];
    }

}
