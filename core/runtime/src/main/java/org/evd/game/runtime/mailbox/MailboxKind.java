package org.evd.game.runtime.mailbox;

public enum MailboxKind {
    PLAYER(1),
    MAP(2),
    GATE(3),
    GUILD(4);

    private final int code;

    MailboxKind(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MailboxKind fromCode(int code) {
        for (MailboxKind kind : values()) {
            if (kind.code == code) {
                return kind;
            }
        }
        throw new IllegalArgumentException("未知的 MailboxKind code: " + code);
    }
}
