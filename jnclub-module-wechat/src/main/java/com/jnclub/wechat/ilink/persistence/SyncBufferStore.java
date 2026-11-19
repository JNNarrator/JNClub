package com.jnclub.wechat.ilink.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 同步缓冲区存储
 */
public class SyncBufferStore implements BufferStore {

    private final Path bufferFile;
    private String buffer;

    public SyncBufferStore(String configDir) {
        this.bufferFile = Paths.get(configDir, "sync_buf.txt");
        this.buffer = load();
    }

    /**
     * 获取当前同步缓冲区值
     */
    public synchronized String getBuffer() {
        return buffer;
    }

    /**
     * 更新同步缓冲区（内存和文件）
     */
    public synchronized void updateBuffer(String newBuffer) {
        if (newBuffer == null || newBuffer.isEmpty()) {
            return;
        }
        this.buffer = newBuffer;
        save();
    }

    /**
     * 清空缓冲区
     */
    public synchronized void clear(){
        this.buffer = "";
		try {
			if (Files.exists(bufferFile)) {
				Files.delete(bufferFile);
			}
		}catch (Exception e){
			throw new RuntimeException("清空缓冲区异常");
		}
    
    }

    public String load() {
        try {
            if (Files.exists(bufferFile)) {
                return Files.readString(bufferFile);
            }
        } catch (IOException e) {
            // 忽略加载错误，返回空字符串
        }
        return "";
    }

    public void save()  {
		try {
			Files.createDirectories(bufferFile.getParent());
			Files.writeString(bufferFile, buffer);
		}catch (Exception e){
			throw  new RuntimeException("保存缓冲区异常");
		}
    
    }
}
