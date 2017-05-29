/*
 * Copyright (C) 2017 Tobias Schülke
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.network;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

import android.net.PskKeyManager;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.RequiresApi;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.TrustManager;
import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.util.Log;


@RequiresApi(api = VERSION_CODES.LOLLIPOP)
public class KeyTransferServerInteractor {
    private static final int SHOW_CONNECTION_DETAILS = 1;
    private static final int CONNECTION_ESTABLISHED = 2;
    public static final int CONNECTION_LOST = 3;


    private Thread socketThread;
    private KeyTransferServerCallback callback;
    private Handler handler;
    private SSLServerSocket serverSocket;

    public void startServer(KeyTransferServerCallback callback) {
        this.callback = callback;

        handler = new Handler(Looper.getMainLooper());
        socketThread = new Thread() {
            @Override
            public void run() {
                serverSocket = null;
                Socket socket = null;
                BufferedReader bufferedReader = null;
                try {
                    int port = 1336;

                    PKM pskKeyManager = new PKM();
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    sslContext.init(new KeyManager[] { pskKeyManager }, new TrustManager[0], null);
                    serverSocket = (SSLServerSocket) sslContext.getServerSocketFactory().createServerSocket(port);

                    String qrCodeData = getIPAddress(true) + ":" + port + ":" + "swag";
                    invokeListener(SHOW_CONNECTION_DETAILS, qrCodeData);

                    socket = serverSocket.accept();
                    invokeListener(CONNECTION_ESTABLISHED, socket.getInetAddress().toString());

                    socket.setSoTimeout(500);
                    bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    while (!isInterrupted() && socket.isConnected()) {
                        try {
                            String line = bufferedReader.readLine();
                            if (line == null) {
                                break;
                            }
                            Log.d(Constants.TAG, "got line: " + line);
                        } catch (SocketTimeoutException e) {
                            // ignore
                        }
                    }
                    Log.d(Constants.TAG, "disconnected");
                    invokeListener(CONNECTION_LOST, null);
                } catch (NoSuchAlgorithmException | KeyManagementException | IOException e) {
                    Log.e(Constants.TAG, "error!", e);
                } finally {
                    try {
                        if (bufferedReader != null) {
                            bufferedReader.close();
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                    try {
                        if (socket != null) {
                            socket.close();
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                    try {
                        if (serverSocket != null) {
                            serverSocket.close();
                        }
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        };

        socketThread.start();
    }

    public void stopServer() {
        if (socketThread != null) {
            socketThread.interrupt();
        }

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // ignore
            }
        }

        socketThread = null;
        serverSocket = null;
        callback = null;
    }

    private void invokeListener(final int method, final String arg) {
        if (handler == null) {
            return;
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                switch (method) {
                    case SHOW_CONNECTION_DETAILS:
                        callback.onServerStarted(arg);
                        break;
                    case CONNECTION_ESTABLISHED:
                        callback.onConnectionEstablished(arg);
                        break;
                    case CONNECTION_LOST:
                        callback.onConnectionLost();
                }
            }
        };

        handler.post(runnable);
    }

    public interface KeyTransferServerCallback {
        void onServerStarted(String qrCodeData);
        void onConnectionEstablished(String otherName);
        void onConnectionLost();
    }

    /**
     * from: http://stackoverflow.com/a/13007325
     * <p>
     * Get IP address from first non-localhost interface
     *
     * @param useIPv4 true=return ipv4, false=return ipv6
     * @return address or empty string
     */
    private static String getIPAddress(boolean useIPv4) {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.
                    getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress()) {
                        String sAddr = addr.getHostAddress();
                        boolean isIPv4 = sAddr.indexOf(':') < 0;

                        if (useIPv4) {
                            if (isIPv4)
                                return sAddr;
                        } else {
                            if (!isIPv4) {
                                int delim = sAddr.indexOf('%'); // drop ip6 zone suffix
                                return delim < 0 ? sAddr.toUpperCase() : sAddr.substring(0, delim).
                                        toUpperCase();
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
        } // for now eat exceptions
        return "";
    }

    private static class PKM extends PskKeyManager implements KeyManager {
        @Override
        public SecretKey getKey(String identityHint, String identity, Socket socket) {
            return new SecretKeySpec("swag".getBytes(), "AES");
        }

        @Override
        public SecretKey getKey(String identityHint, String identity, SSLEngine engine) {
            return new SecretKeySpec("swag".getBytes(), "AES");
        }
    }
}
