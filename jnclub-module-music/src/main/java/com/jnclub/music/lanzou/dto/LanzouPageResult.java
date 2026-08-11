package com.jnclub.music.lanzou.dto;

import java.util.List;

public record LanzouPageResult(int page, int pageSize, List<LanzouFile> files, List<LanzouFolder> folders) {}
