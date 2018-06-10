package com.github.xy02.nats;

import io.reactivex.Completable;

public class MSG implements Message {
    private String subject;
    private int sid;
    private String replyTo;
    private byte[] body;

    public MSG(String subject, byte[] body) {
        this(subject, "", body);
    }

    public MSG(String subject, String replyTo, byte[] body) {
        this(subject, 0, replyTo, body);
    }

    @Override
    public Completable handle(Connection connection) {
        return Completable.create(emitter -> {
            System.out.println(new String(body));
            emitter.onComplete();
        });
    }

    protected MSG(String subject, int sid, String replyTo, byte[] body) {
        this.subject = subject;
        this.sid = sid;
        this.replyTo = replyTo;
        this.body = body;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public int getSid() {
        return sid;
    }

    public void setSid(int sid) {
        this.sid = sid;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

}
