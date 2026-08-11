package com.jnclub.music.track.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jnclub.music.track.domain.Track;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TrackMapper extends BaseMapper<Track> {

    @Update("UPDATE music_track SET media_url = NULL, url_expires_at = NULL")
    void clearAllMediaUrls();
}
