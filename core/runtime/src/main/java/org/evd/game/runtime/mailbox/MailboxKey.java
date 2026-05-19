package org.evd.game.runtime.mailbox;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.evd.game.annotation.SerializeClass;
import org.evd.game.annotation.SerializeField;
import org.evd.game.base.ISerializable;

import java.util.Objects;

@SerializeClass
public class MailboxKey implements ISerializable {
    @SerializeField
    private int kindCode;
    @SerializeField
    private long id;

    public MailboxKey() {
    }

    public MailboxKey(MailboxKind kind, long id) {
        this(kind.getCode(), id);
    }

    public MailboxKey(int kindCode, long id) {
        this.kindCode = kindCode;
        this.id = id;
    }

    public MailboxKey(MailboxKey other) {
        this(other.kindCode, other.id);
    }

    public static MailboxKey player(long id) {
        return new MailboxKey(MailboxKind.PLAYER, id);
    }

    public static MailboxKey map(long id) {
        return new MailboxKey(MailboxKind.MAP, id);
    }

    public static MailboxKey gate(long id) {
        return new MailboxKey(MailboxKind.GATE, id);
    }

    public MailboxKind getKind() {
        return MailboxKind.fromCode(kindCode);
    }

    public void setKind(MailboxKind kind) {
        this.kindCode = kind.getCode();
    }

    public int getKindCode() {
        return kindCode;
    }

    public void setKindCode(int kindCode) {
        this.kindCode = kindCode;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MailboxKey that)) {
            return false;
        }
        return kindCode == that.kindCode && id == that.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(kindCode, id);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("kind", getKind())
                .append("id", id)
                .toString();
    }
}
