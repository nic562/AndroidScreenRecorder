package io.github.nic562.screen.recorder.db.dao;

import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;

import org.greenrobot.greendao.AbstractDao;
import org.greenrobot.greendao.Property;
import org.greenrobot.greendao.internal.DaoConfig;
import org.greenrobot.greendao.database.Database;
import org.greenrobot.greendao.database.DatabaseStatement;

import io.github.nic562.screen.recorder.db.ApiInfo.Method;
import io.github.nic562.screen.recorder.db.ApiInfo.Method.MethodConverter;

import io.github.nic562.screen.recorder.db.ApiInfo;

// THIS CODE IS GENERATED BY greenDAO, DO NOT EDIT.
/** 
 * DAO for table "API_INFO".
*/
public class ApiInfoDao extends AbstractDao<ApiInfo, Long> {

    public static final String TABLENAME = "API_INFO";

    /**
     * Properties of entity ApiInfo.<br/>
     * Can be used for QueryBuilder and for referencing column names.
     */
    public static class Properties {
        public final static Property Id = new Property(0, Long.class, "id", true, "_id");
        public final static Property Title = new Property(1, String.class, "title", false, "TITLE");
        public final static Property Url = new Property(2, String.class, "url", false, "URL");
        public final static Property Method = new Property(3, String.class, "method", false, "METHOD");
        public final static Property Header = new Property(4, String.class, "header", false, "HEADER");
        public final static Property Body = new Property(5, String.class, "body", false, "BODY");
        public final static Property UploadFileArgName = new Property(6, String.class, "uploadFileArgName", false, "UPLOAD_FILE_ARG_NAME");
    }

    private final MethodConverter methodConverter = new MethodConverter();

    public ApiInfoDao(DaoConfig config) {
        super(config);
    }
    
    public ApiInfoDao(DaoConfig config, DaoSession daoSession) {
        super(config, daoSession);
    }

    /** Creates the underlying database table. */
    public static void createTable(Database db, boolean ifNotExists) {
        String constraint = ifNotExists? "IF NOT EXISTS ": "";
        db.execSQL("CREATE TABLE " + constraint + "\"API_INFO\" (" + //
                "\"_id\" INTEGER PRIMARY KEY AUTOINCREMENT ," + // 0: id
                "\"TITLE\" TEXT NOT NULL ," + // 1: title
                "\"URL\" TEXT NOT NULL ," + // 2: url
                "\"METHOD\" TEXT NOT NULL ," + // 3: method
                "\"HEADER\" TEXT," + // 4: header
                "\"BODY\" TEXT," + // 5: body
                "\"UPLOAD_FILE_ARG_NAME\" TEXT NOT NULL );"); // 6: uploadFileArgName
        // Add Indexes
        db.execSQL("CREATE UNIQUE INDEX " + constraint + "IDX_API_INFO_TITLE ON \"API_INFO\"" +
                " (\"TITLE\" ASC);");
        db.execSQL("CREATE INDEX " + constraint + "IDX_API_INFO_METHOD ON \"API_INFO\"" +
                " (\"METHOD\" ASC);");
        db.execSQL("CREATE INDEX " + constraint + "IDX_API_INFO_UPLOAD_FILE_ARG_NAME ON \"API_INFO\"" +
                " (\"UPLOAD_FILE_ARG_NAME\" ASC);");
    }

    /** Drops the underlying database table. */
    public static void dropTable(Database db, boolean ifExists) {
        String sql = "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + "\"API_INFO\"";
        db.execSQL(sql);
    }

    @Override
    protected final void bindValues(DatabaseStatement stmt, ApiInfo entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
        stmt.bindString(2, entity.getTitle());
        stmt.bindString(3, entity.getUrl());
        stmt.bindString(4, methodConverter.convertToDatabaseValue(entity.getMethod()));
 
        String header = entity.getHeader();
        if (header != null) {
            stmt.bindString(5, header);
        }
 
        String body = entity.getBody();
        if (body != null) {
            stmt.bindString(6, body);
        }
        stmt.bindString(7, entity.getUploadFileArgName());
    }

    @Override
    protected final void bindValues(SQLiteStatement stmt, ApiInfo entity) {
        stmt.clearBindings();
 
        Long id = entity.getId();
        if (id != null) {
            stmt.bindLong(1, id);
        }
        stmt.bindString(2, entity.getTitle());
        stmt.bindString(3, entity.getUrl());
        stmt.bindString(4, methodConverter.convertToDatabaseValue(entity.getMethod()));
 
        String header = entity.getHeader();
        if (header != null) {
            stmt.bindString(5, header);
        }
 
        String body = entity.getBody();
        if (body != null) {
            stmt.bindString(6, body);
        }
        stmt.bindString(7, entity.getUploadFileArgName());
    }

    @Override
    public Long readKey(Cursor cursor, int offset) {
        return cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0);
    }    

    @Override
    public ApiInfo readEntity(Cursor cursor, int offset) {
        ApiInfo entity = new ApiInfo( //
            cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0), // id
            cursor.getString(offset + 1), // title
            cursor.getString(offset + 2), // url
            methodConverter.convertToEntityProperty(cursor.getString(offset + 3)), // method
            cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4), // header
            cursor.isNull(offset + 5) ? null : cursor.getString(offset + 5), // body
            cursor.getString(offset + 6) // uploadFileArgName
        );
        return entity;
    }
     
    @Override
    public void readEntity(Cursor cursor, ApiInfo entity, int offset) {
        entity.setId(cursor.isNull(offset + 0) ? null : cursor.getLong(offset + 0));
        entity.setTitle(cursor.getString(offset + 1));
        entity.setUrl(cursor.getString(offset + 2));
        entity.setMethod(methodConverter.convertToEntityProperty(cursor.getString(offset + 3)));
        entity.setHeader(cursor.isNull(offset + 4) ? null : cursor.getString(offset + 4));
        entity.setBody(cursor.isNull(offset + 5) ? null : cursor.getString(offset + 5));
        entity.setUploadFileArgName(cursor.getString(offset + 6));
     }
    
    @Override
    protected final Long updateKeyAfterInsert(ApiInfo entity, long rowId) {
        entity.setId(rowId);
        return rowId;
    }
    
    @Override
    public Long getKey(ApiInfo entity) {
        if(entity != null) {
            return entity.getId();
        } else {
            return null;
        }
    }

    @Override
    public boolean hasKey(ApiInfo entity) {
        return entity.getId() != null;
    }

    @Override
    protected final boolean isEntityUpdateable() {
        return true;
    }
    
}