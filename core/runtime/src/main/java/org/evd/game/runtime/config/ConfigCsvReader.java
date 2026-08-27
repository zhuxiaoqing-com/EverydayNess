package org.evd.game.runtime.config;

import nbbrd.picocsv.Csv;

import java.io.IOException;
import java.io.Reader;
import java.util.Objects;

/** 由 runtime 持有的适配器，避免生成的业务代码直接依赖 picocsv。 */
public final class ConfigCsvReader implements AutoCloseable {
    private final Csv.Reader reader;

    private ConfigCsvReader(Csv.Reader reader) {
        this.reader = reader;
    }

    /**
     * 创建配置 CSV 读取器。
     *
     * <p>picocsv 默认使用逗号分隔字段和 Windows 换行符，
     * 这里开启宽松换行模式来同时接受 Windows、Linux 和旧 Mac 的换行格式，
     * 并禁止一行中缺少字段。</p>
     *
     * @param source 配置文件字符流
     * @return 配置 CSV 读取器
     * @throws IOException 创建 CSV 读取器失败时抛出
     */
    public static ConfigCsvReader open(Reader source) throws IOException {
        Objects.requireNonNull(source, "source");
        Csv.Format format = Csv.Format.builder()
                // 配置表要求每一行的字段数量完整，避免缺列时静默读取。
                .acceptMissingField(false)
                .build();
        Csv.ReaderOptions options = Csv.ReaderOptions.builder()
                .lenientSeparator(true)
                .build();
        return new ConfigCsvReader(Csv.Reader.of(format, options, source));
    }

    public boolean readLine() throws IOException {
        return reader.readLine();
    }

    /**
     * 判断当前读取的行是否是注释行。
     *
     * <p>注释行由 picocsv 按默认的 {@code #} 开头规则识别；该方法只返回当前行状态，
     * 不会读取下一行或改变读取位置。</p>
     *
     * @return 当前行是注释行时返回 {@code true}
     */
    public boolean isComment() {
        return reader.isComment();
    }

    public boolean readField() throws IOException {
        return reader.readField();
    }

    /**
     * 返回当前字段内容：未加引号的字段去除首尾空白，加引号的字段保留原始空白。
     */
    public CharSequence field() {
        // reader.isQuoted() 用来判断“当前 CSV 字段是否使用了双引号包裹”。 里面有需要转移的字符的情况下 会加双引号;
        return reader.isQuoted() ? reader : reader.toString().trim();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
