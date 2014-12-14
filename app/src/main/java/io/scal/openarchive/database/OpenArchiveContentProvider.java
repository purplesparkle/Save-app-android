package io.scal.openarchive.database;

import android.net.Uri;

import net.simonvt.schematic.annotation.ContentProvider;
import net.simonvt.schematic.annotation.ContentUri;
import net.simonvt.schematic.annotation.TableEndpoint;

/**
 * Created by micahjlucas on 12/12/14.
 */
@ContentProvider(authority = OpenArchiveContentProvider.AUTHORITY, database = OpenArchiveDatabase.class)
public final class OpenArchiveContentProvider {

    public static final String AUTHORITY = "io.scal.openarchive.OpenArchiveContentProvider";
    private static final Uri BASE_CONTENT_URI = Uri.parse("content://" + AUTHORITY);


    //helper class
    private static Uri buildUri(String... paths) {
        Uri.Builder builder = BASE_CONTENT_URI.buildUpon();
        for (String path : paths) {
            builder.appendPath(path);
        }
        return builder.build();
    }

    //user api
    @TableEndpoint(table = OpenArchiveDatabase.USER)
    public static class User {

        private static final String ENDPOINT = "user";

        @ContentUri(
                path = ENDPOINT,
                type = "vnd.android.cursor.dir/list",
                defaultSort = UserTable.username + " ASC")
        public static final Uri USER = buildUri(ENDPOINT);
    }

    //media api
    @TableEndpoint(table = OpenArchiveDatabase.MEDIA)
    public static class Media {

        private static final String ENDPOINT = "media";

        @ContentUri(
                path = ENDPOINT,
                type = "vnd.android.cursor.dir/list",
                defaultSort = MediaTable.createdDate + " ASC")
        public static final Uri MEDIA = buildUri(ENDPOINT);
    }
}
