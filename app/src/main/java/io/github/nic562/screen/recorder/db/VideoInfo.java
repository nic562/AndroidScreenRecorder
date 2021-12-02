package io.github.nic562.screen.recorder.db;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Index;
import org.greenrobot.greendao.annotation.NotNull;

import java.io.File;
import java.util.Date;

import org.greenrobot.greendao.annotation.Generated;

import io.github.nic562.screen.recorder.db.dao.VideoInfoDao;

@Entity(indexes = {
        @Index(value = "createTime", unique = true)
})
public class VideoInfo {
    @Id(autoincrement = true)
    private Long id;

    @NotNull
    private String filePath;

    @NotNull
    private Date createTime;

    private String previewPath;

    @Generated(hash = 1710493138)
    public VideoInfo(Long id, @NotNull String filePath, @NotNull Date createTime,
                     String previewPath) {
        this.id = id;
        this.filePath = filePath;
        this.createTime = createTime;
        this.previewPath = previewPath;
    }

    @Generated(hash = 296402066)
    public VideoInfo() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilePath() {
        return this.filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public Date getCreateTime() {
        return this.createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getPreviewPath() {
        return this.previewPath;
    }

    public void setPreviewPath(String previewPath) {
        this.previewPath = previewPath;
    }

    public void destroy(VideoInfoDao dao) {
        File fv = new File(filePath);
        if (fv.exists()) {
            fv.delete();
        }
        File fi = new File(previewPath);
        if (fi.exists()) {
            fi.delete();
        }
        dao.delete(this);
    }
}
