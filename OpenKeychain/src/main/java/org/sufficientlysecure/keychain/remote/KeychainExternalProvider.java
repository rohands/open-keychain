/*
 * Copyright (C) 2012-2014 Dominik Schürmann <dominik@dominikschuermann.de>
 * Copyright (C) 2010-2014 Thialfihar <thi@thialfihar.org>
 * Copyright (C) 2014 Vincent Breitmoser <v.breitmoser@mugenguild.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.sufficientlysecure.keychain.remote;


import java.util.Arrays;
import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.provider.KeychainContract.Certs;
import org.sufficientlysecure.keychain.provider.KeychainContract.KeyRings;
import org.sufficientlysecure.keychain.provider.KeychainContract.UserPackets;
import org.sufficientlysecure.keychain.provider.KeychainDatabase;
import org.sufficientlysecure.keychain.provider.KeychainDatabase.Tables;
import org.sufficientlysecure.keychain.provider.KeychainExternalContract;
import org.sufficientlysecure.keychain.provider.KeychainExternalContract.EmailStatus;
import org.sufficientlysecure.keychain.util.Log;


public class KeychainExternalProvider extends ContentProvider {
    private static final int EMAIL_STATUS = 101;


    protected UriMatcher mUriMatcher;


    /**
     * Build and return a {@link UriMatcher} that catches all {@link Uri} variations supported by
     * this {@link ContentProvider}.
     */
    protected UriMatcher buildUriMatcher() {
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

        String authority = KeychainExternalContract.CONTENT_AUTHORITY_EXTERNAL;

        /**
         * list email_status
         *
         * <pre>
         * email_status/
         * </pre>
         */
        matcher.addURI(authority, KeychainExternalContract.BASE_EMAIL_STATUS, EMAIL_STATUS);

        return matcher;
    }

    private KeychainDatabase mKeychainDatabase;

    /** {@inheritDoc} */
    @Override
    public boolean onCreate() {
        mUriMatcher = buildUriMatcher();
        return true;
    }

    public KeychainDatabase getDb() {
        if(mKeychainDatabase == null)
            mKeychainDatabase = new KeychainDatabase(getContext());
        return mKeychainDatabase;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType(@NonNull Uri uri) {
        final int match = mUriMatcher.match(uri);
        switch (match) {
            case EMAIL_STATUS:
                return EmailStatus.CONTENT_TYPE;

            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        Log.v(Constants.TAG, "query(uri=" + uri + ", proj=" + Arrays.toString(projection) + ")");

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        int match = mUriMatcher.match(uri);

        String groupBy;

        switch (match) {
            case EMAIL_STATUS: {
                HashMap<String, String> projectionMap = new HashMap<>();
                projectionMap.put(EmailStatus._ID, "email AS _id");
                projectionMap.put(EmailStatus.EMAIL_ADDRESS,
                        Tables.USER_PACKETS + "." + UserPackets.USER_ID + " AS " + EmailStatus.EMAIL_ADDRESS);
                projectionMap.put(EmailStatus.EMAIL_KEY_REFERENCE,
                        Tables.USER_PACKETS + "." + UserPackets.MASTER_KEY_ID + " AS " + EmailStatus.EMAIL_KEY_REFERENCE);
                // we take the minimum (>0) here, where "1" is "verified by known secret key", "2" is "self-certified"
                projectionMap.put(EmailStatus.EMAIL_STATUS, "CASE ( MIN (" + Certs.VERIFIED + " ) ) "
                        // remap to keep this provider contract independent from our internal representation
                        + " WHEN " + Certs.VERIFIED_SELF + " THEN 1"
                        + " WHEN " + Certs.VERIFIED_SECRET + " THEN 2"
                        + " END AS " + EmailStatus.EMAIL_STATUS);
                qb.setProjectionMap(projectionMap);

                if (projection == null) {
                    throw new IllegalArgumentException("Please provide a projection!");
                }

                qb.setTables(
                        Tables.USER_PACKETS
                                + " INNER JOIN " + Tables.CERTS + " ON ("
                                + Tables.USER_PACKETS + "." + UserPackets.MASTER_KEY_ID + " = "
                                + Tables.CERTS + "." + Certs.MASTER_KEY_ID
                                + " AND " + Tables.USER_PACKETS + "." + UserPackets.RANK + " = "
                                + Tables.CERTS + "." + Certs.RANK
                                // verified == 0 has no self-cert, which is basically an error case. never return that!
                                + " AND " + Tables.CERTS + "." + Certs.VERIFIED + " > 0"
                                + ")"
                );
                qb.appendWhere(Tables.USER_PACKETS + "." + UserPackets.USER_ID + " IS NOT NULL");
                // in case there are multiple verifying certificates
                groupBy = Tables.USER_PACKETS + "." + UserPackets.MASTER_KEY_ID + ", "
                        + Tables.USER_PACKETS + "." + UserPackets.USER_ID;

                if (TextUtils.isEmpty(sortOrder)) {
                    sortOrder =  EmailStatus.EMAIL_ADDRESS + " ASC, " + EmailStatus.EMAIL_STATUS + " DESC";
                }

                // uri to watch is all /key_rings/
                uri = KeyRings.CONTENT_URI;

                boolean gotCondition = false;
                String emailWhere = "";
                // JAVA ♥
                for (int i = 0; i < selectionArgs.length; ++i) {
                    if (selectionArgs[i].length() == 0) {
                        continue;
                    }
                    if (i != 0) {
                        emailWhere += " OR ";
                    }
                    emailWhere += UserPackets.USER_ID + " LIKE ";
                    // match '*<email>', so it has to be at the *end* of the user id
                    emailWhere += DatabaseUtils.sqlEscapeString("%<" + selectionArgs[i] + ">");
                    gotCondition = true;
                }

                if (gotCondition) {
                    qb.appendWhere(" AND (" + emailWhere + ")");
                } else {
                    // TODO better way to do this?
                    Log.e(Constants.TAG, "Malformed find by email query!");
                    qb.appendWhere(" AND 0");
                }

                break;
            }

            default: {
                throw new IllegalArgumentException("Unknown URI " + uri + " (" + match + ")");
            }

        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = null;
        } else {
            orderBy = sortOrder;
        }

        SQLiteDatabase db = getDb().getReadableDatabase();

        Cursor cursor = qb.query(db, projection, selection, null, groupBy, null, orderBy);
        if (cursor != null) {
            // Tell the cursor what uri to watch, so it knows when its source data changes
            cursor.setNotificationUri(getContext().getContentResolver(), uri);
        }

        Log.d(Constants.TAG,
                "Query: " + qb.buildQuery(projection, selection, null, null, orderBy, null));

        return cursor;
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(@NonNull Uri uri, String additionalSelection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

}
