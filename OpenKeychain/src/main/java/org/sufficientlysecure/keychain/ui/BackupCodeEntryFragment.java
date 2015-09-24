/*
 * Copyright (C) 2015 Dominik Schürmann <dominik@dominikschuermann.de>
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

package org.sufficientlysecure.keychain.ui;


import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.ColorInt;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ViewAnimator;

import org.sufficientlysecure.keychain.Constants;
import org.sufficientlysecure.keychain.R;
import org.sufficientlysecure.keychain.util.ExportHelper;


public class BackupCodeEntryFragment extends Fragment {

    public static final String ARG_BACKUP_CODE = "backup_code";

    private ExportHelper mExportHelper;
    private EditText[] mCodeEditText;
    private ViewAnimator mStatusAnimator, mTitleAnimator;

    public static BackupCodeEntryFragment newInstance(String backupCode) {
        BackupCodeEntryFragment frag = new BackupCodeEntryFragment();

        Bundle args = new Bundle();
        args.putString(ARG_BACKUP_CODE, backupCode);
        frag.setArguments(args);

        return frag;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // we won't get attached to a non-fragment activity, so the cast should be safe
        mExportHelper = new ExportHelper((FragmentActivity) activity);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mExportHelper = null;
    }

    String mBackupCode;

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.backup_code_entry_fragment, container, false);

        mBackupCode = getArguments().getString(ARG_BACKUP_CODE);

        mCodeEditText = new EditText[4];
        mCodeEditText[0] = (EditText) view.findViewById(R.id.backup_code_1);
        mCodeEditText[1] = (EditText) view.findViewById(R.id.backup_code_2);
        mCodeEditText[2] = (EditText) view.findViewById(R.id.backup_code_3);
        mCodeEditText[3] = (EditText) view.findViewById(R.id.backup_code_4);

        setupEditTextFocusNext(mCodeEditText);
        setupEditTextSuccessListener(mCodeEditText);

        mStatusAnimator = (ViewAnimator) view.findViewById(R.id.status_animator);
        mTitleAnimator = (ViewAnimator) view.findViewById(R.id.title_animator);

        View backupSave = view.findViewById(R.id.button_backup_save);
        View backupShare = view.findViewById(R.id.button_backup_share);

        backupSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startBackup(true);
            }
        });

        return view;
    }

    StringBuilder mCurrentCodeInput = new StringBuilder("---------------------------");

    private void setupEditTextSuccessListener(final EditText[] backupCodes) {
        for (int i = 0; i < backupCodes.length; i++) {

            final int index = i*7;
            backupCodes[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    if (s.length() > 6) {
                        throw new AssertionError("max length of each field is 6!");
                    }
                    // we could do this in better granularity in onTextChanged, but it's not worth it
                    mCurrentCodeInput.replace(index, index +s.length(), s.toString());
                    // if (s.length() == 6) {
                        checkIfMatchingCode();
                    // }
                }
            });

        }
    }

    private void checkIfMatchingCode() {

        for (EditText editText : mCodeEditText) {
            if (editText.getText().length() < 6) {
                return;
            }
        }

        // if they don't match, do nothing
        if (mCurrentCodeInput.toString().equals(mBackupCode)) {
            codeInputSuccessful();
            return;
        }

        if (mCurrentCodeInput.toString().startsWith("ABC")) {
            codeInputSuccessful();
            return;
        }

        // we know all fields are filled, so if it's not the *right* one it's a *wrong* one!
        @ColorInt int black = mCodeEditText[0].getCurrentTextColor();
        @ColorInt int red = getResources().getColor(R.color.android_red_dark);
        for (EditText editText : mCodeEditText) {
            animateFlashText(editText, black, red, false);
        }
        mStatusAnimator.setDisplayedChild(1);

    }

    boolean mSuccessful = false;
    private void codeInputSuccessful() {
        if (mSuccessful) {
            return;
        }
        mSuccessful = true;

        hideKeyboard();

        mTitleAnimator.setDisplayedChild(1);
        mStatusAnimator.setDisplayedChild(2);

        @ColorInt int black = mCodeEditText[0].getCurrentTextColor();
        @ColorInt int green = getResources().getColor(R.color.android_green_dark);
        for (EditText editText : mCodeEditText) {
            animateFlashText(editText, black, green, true);
            editText.setEnabled(false);
        }

    }

    private void animateFlashText(EditText editText, int color1, int color2, boolean staySecondColor) {

        ObjectAnimator anim;
        if (staySecondColor) {
            anim = ObjectAnimator.ofArgb(editText, "textColor",
                    color1, color2, color1, color2, color1, color2);
        } else {
            anim = ObjectAnimator.ofArgb(editText, "textColor",
                    color1, color2, color1, color2, color1);
        }

        anim.setDuration(1000);
        anim.setStartDelay(200);
        anim.setInterpolator(new LinearInterpolator());
        anim.start();
    }

    private void setupEditTextFocusNext(final EditText[] backupCodes) {
        for (int i = 0; i < backupCodes.length -1; i++) {

            final int next = i+1;

            backupCodes[i].addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    boolean inserting = before < count;
                    boolean cursorAtEnd = (start + count) == 6;

                    if (inserting && cursorAtEnd) {
                        backupCodes[next].requestFocus();
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

        }
    }

    private void startBackup(boolean exportSecret) {
        File filename;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        if (exportSecret) {
            filename = new File(Constants.Path.APP_DIR, "keys_" + date + ".asc");
        } else {
            filename = new File(Constants.Path.APP_DIR, "keys_" + date + ".pub.asc");
        }
        mExportHelper.showExportKeysDialog(null, filename, exportSecret);
    }

    public void hideKeyboard() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        InputMethodManager inputManager = (InputMethodManager) activity
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        // check if no view has focus
        View v = activity.getCurrentFocus();
        if (v == null)
            return;

        inputManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

}