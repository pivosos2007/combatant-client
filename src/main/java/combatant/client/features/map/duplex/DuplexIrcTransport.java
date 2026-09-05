/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.map.duplex;

import combatant.client.config.subsystem.DuplexIrcConfig;

import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class DuplexIrcTransport implements AutoCloseable {
    private static final int MAX_IRC_LINE = 510;

    private final DuplexIrcConfig config;
    private final ArrayBlockingQueue<String> outbound = new ArrayBlockingQueue<>(128);
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Socket socket;
    private volatile Consumer<String> messageSink = ignored -> {};
    private Thread ioThread;

    public DuplexIrcTransport(DuplexIrcConfig config) {
        this.config = config;
    }

    public void setMessageSink(Consumer<String> sink) {
        this.messageSink = sink == null ? ignored -> {} : sink;
    }

    public synchronized void start() {
        if (!running.compareAndSet(false,true)) return;
        ioThread=new Thread(this::runLoop,"Combatant-Duplex-IRC");
        ioThread.setDaemon(true);
        ioThread.start();
    }

    public boolean sendPayload(String payload) {
        if(payload==null || payload.length()>360) return false;
        return outbound.offer(payload);
    }

    private void runLoop() {
        try(Socket s=openSocket();
            BufferedReader in=new BufferedReader(new InputStreamReader(s.getInputStream(),StandardCharsets.UTF_8));
            BufferedWriter out=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),StandardCharsets.UTF_8))) {
            socket=s;
            write(out,"NICK "+nick());
            write(out,"USER "+nick()+" 0 * :"+nick());
            write(out,"JOIN "+channel());

            while(running.get()) {
                while(in.ready()) {
                    String line=in.readLine();
                    if(line==null) return;
                    if(line.startsWith("PING ")) { write(out,"PONG "+line.substring(5)); continue; }
                    String marker=" PRIVMSG "+channel()+" :";
                    int idx=line.indexOf(marker);
                    if(idx>=0) messageSink.accept(line.substring(idx+marker.length()));
                }
                String payload=outbound.poll();
                if(payload!=null) write(out,"PRIVMSG "+channel()+" :"+payload);
                else Thread.sleep(10L);
            }
        } catch(InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch(IOException ignored) {
        } finally {
            running.set(false);
            socket=null;
        }
    }

    private Socket openSocket() throws IOException {
        if(config.host().isBlank()) throw new IOException("IRC host is blank");
        return config.tls()
                ? SSLSocketFactory.getDefault().createSocket(config.host(),config.port())
                : new Socket(config.host(),config.port());
    }

    private String channel() {
        String c=config.channel();
        if(c.isBlank()) throw new IllegalStateException("IRC channel is blank");
        return c.charAt(0)=='#'?c:"#"+c;
    }

    private String nick() {
        String n=config.nickname().replaceAll("[^A-Za-z0-9_\\-]","_");
        return n.isBlank()?"combatant_"+Integer.toHexString(System.identityHashCode(this)):n;
    }

    private static void write(BufferedWriter out,String line)throws IOException {
        if(line.length()>MAX_IRC_LINE) throw new IOException("IRC line too long");
        out.write(line); out.write("\r\n"); out.flush();
    }

    @Override public synchronized void close() {
        running.set(false);
        if(ioThread!=null) ioThread.interrupt();
        Socket s=socket;
        if(s!=null) try{s.close();}catch(IOException ignored){}
        outbound.clear();
    }
}
