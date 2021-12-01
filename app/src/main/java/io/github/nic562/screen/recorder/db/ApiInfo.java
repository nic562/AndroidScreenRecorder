package io.github.nic562.screen.recorder.db;

import org.greenrobot.greendao.annotation.Convert;
import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Index;
import org.greenrobot.greendao.annotation.NotNull;
import org.greenrobot.greendao.converter.PropertyConverter;

import java.util.Arrays;
import java.util.List;

import org.greenrobot.greendao.annotation.Generated;

@Entity(indexes = {
        @Index(value = "title", unique = true),
        @Index(value = "method"),
        @Index(value = "uploadFileArgName")
})
public class ApiInfo {

    public enum Method {
        GET, POST;
        public static List<Method> ALL = Arrays.asList(GET, POST);

        public static class MethodConverter implements PropertyConverter<Method, String> {

            @Override
            public Method convertToEntityProperty(String databaseValue) {
                return Method.valueOf(databaseValue);
            }

            @Override
            public String convertToDatabaseValue(Method entityProperty) {
                return entityProperty.name();
            }
        }
    }

    @Id(autoincrement = true)
    private Long id;

    @NotNull
    private String title;

    @NotNull
    private String url;

    @NotNull
    @Convert(converter = Method.MethodConverter.class, columnType = String.class)
    private Method method;

    private String header;

    private String body;

    private Boolean isBodyEncoding;

    @NotNull
    private String uploadFileArgName;

    @Generated(hash = 689619700)
    public ApiInfo(Long id, @NotNull String title, @NotNull String url,
                   @NotNull Method method, String header, String body, Boolean isBodyEncoding,
                   @NotNull String uploadFileArgName) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.method = method;
        this.header = header;
        this.body = body;
        this.isBodyEncoding = isBodyEncoding;
        this.uploadFileArgName = uploadFileArgName;
    }

    @Generated(hash = 1876006400)
    public ApiInfo() {
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Method getMethod() {
        return this.method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public String getHeader() {
        return this.header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getBody() {
        return this.body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getUploadFileArgName() {
        return this.uploadFileArgName;
    }

    public void setUploadFileArgName(String uploadFileArgName) {
        this.uploadFileArgName = uploadFileArgName;
    }

    public Boolean getIsBodyEncoding() {
        return this.isBodyEncoding;
    }

    public void setIsBodyEncoding(Boolean isBodyEncoding) {
        this.isBodyEncoding = isBodyEncoding;
    }
}
