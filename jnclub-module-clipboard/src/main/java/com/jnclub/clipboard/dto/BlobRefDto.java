package com.jnclub.clipboard.dto;

import lombok.Data;

/**
 * 附件引用：客户端上传完 dufs 后，用这个结构把「我引用了哪些 blob」告诉服务端。
 */
@Data
public class BlobRefDto {

    private String sha256;

    private Long byteSize;

    private String mime;
}
