package com.jnclub.wechat.ilink.persistence;

/**
 * Created By JNNarrator on 2026/5/6 15:47
 * email: jiangn09@chinatelecom.cn
 */
public interface BufferStore {
	String getBuffer();
	void updateBuffer(String newBuffer);
	void clear();
	String load();
	void save();
}
