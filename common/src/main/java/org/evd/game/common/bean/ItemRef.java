package org.evd.game.common.bean;

import org.evd.game.annotation.config.ConfigConverter;
import org.evd.game.annotation.config.ConfigDefaultConverter;
import org.evd.game.annotation.serialize.SerializeClass;
import org.evd.game.base.ISerializable;
import org.evd.game.runtime.config.ConfigValueParser;

/** 配置表中引用道具及数量的通用值对象。 */
@ConfigDefaultConverter(ItemRef.class)
@SerializeClass
public class ItemRef implements ConfigConverter<ItemRef>, ISerializable {
    public int itemId;
    public int count;

    public ItemRef() {
    }

    public ItemRef(int itemId, int count) {
        this.itemId = itemId;
        this.count = count;
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    @Override
    public ItemRef convert(CharSequence value) {
        int separator = indexOf(value, '&');
        if (separator < 0) {
            throw new IllegalArgumentException("道具引用格式必须是 itemId&count: " + value);
        }
        int itemId = ConfigValueParser.parseInt(value.subSequence(0, separator));
        int count = ConfigValueParser.parseInt(value.subSequence(separator + 1, value.length()));
        return new ItemRef(itemId, count);
    }

    private static int indexOf(CharSequence value, char target) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == target) {
                return i;
            }
        }
        return -1;
    }
}
