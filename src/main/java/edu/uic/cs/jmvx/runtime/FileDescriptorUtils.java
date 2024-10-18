package edu.uic.cs.jmvx.runtime;

import sun.misc.JavaIOFileDescriptorAccess;
import sun.misc.SharedSecrets;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketImpl;

public class FileDescriptorUtils {

    public static final JavaIOFileDescriptorAccess fdAccess = SharedSecrets.getJavaIOFileDescriptorAccess();

    public static FileDescriptor getFileDescriptor(Socket sock) throws NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        //could use the getImpl method alternatively
        Field implField = Socket.class.getDeclaredField("impl");
        implField.setAccessible(true);

        Method get = SocketImpl.class.getDeclaredMethod("getFileDescriptor");
        get.setAccessible(true);

        return (FileDescriptor) get.invoke(implField.get(sock));
    }

    public static void setFileDescriptor(Socket sock, FileDescriptor newFD) throws NoSuchFieldException, IllegalAccessException {
        Field implField = Socket.class.getDeclaredField("impl");
        implField.setAccessible(true);

        Field fdField = SocketImpl.class.getDeclaredField("fd");
        fdField.setAccessible(true);

        fdField.set(implField.get(sock), newFD);
    }

    public static FileDescriptor getFileDescriptor(ServerSocket serv) throws NoSuchFieldException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
        //could use the getImpl method alternatively
        Field implField = ServerSocket.class.getDeclaredField("impl");
        implField.setAccessible(true);

        Method get = SocketImpl.class.getDeclaredMethod("getFileDescriptor");
        get.setAccessible(true);

        return (FileDescriptor) get.invoke(implField.get(serv));
    }

    public static void setFileDescriptor(ServerSocket serv, FileDescriptor newFD) throws NoSuchFieldException, IllegalAccessException {
        Field implField = ServerSocket.class.getDeclaredField("impl");
        implField.setAccessible(true);

        Field fdField = SocketImpl.class.getDeclaredField("fd");
        fdField.setAccessible(true);

        fdField.set(implField.get(serv), newFD);
    }

    public static void closeFd(FileDescriptor fd) throws IOException{
        FileInputStream closeMe = new FileInputStream(fd);
        closeMe.close();
    }

    public static void swapFd(Socket sock, FileDescriptor newFd, boolean close) throws IOException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        if(close)
            closeFd(getFileDescriptor(sock));

        setFileDescriptor(sock, newFd);
    }

    public static void swapFd(ServerSocket serv, FileDescriptor newFd, boolean close) throws IOException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        if(close)
            closeFd(getFileDescriptor(serv));

        setFileDescriptor(serv, newFd);
    }

    public static void setPort(ServerSocket sock, int newPort) throws NoSuchFieldException, IllegalAccessException {
        Field implField = ServerSocket.class.getDeclaredField("impl");
        implField.setAccessible(true);

        Field portField = SocketImpl.class.getDeclaredField("localport");
        portField.setAccessible(true);

        portField.set(implField.get(sock), newPort);
    }

    public static void setPort(Socket sock, int newPort) throws NoSuchFieldException, IllegalAccessException {
        Field implField = Socket.class.getDeclaredField("impl");
        implField.setAccessible(true);

        Field portField = SocketImpl.class.getDeclaredField("localport");
        portField.setAccessible(true);

        portField.set(implField.get(sock), newPort);
    }
}
