/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.view.textclassifier;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.ArrayUtils;
import com.android.internal.util.Preconditions;

import com.google.android.textclassifier.AnnotatorModel;
import com.google.android.textclassifier.NamedVariant;
import com.google.android.textclassifier.RemoteActionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Creates intents based on {@link RemoteActionTemplate} objects.
 * @hide
 */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public final class TemplateIntentFactory implements IntentFactory {
    private static final String TAG = TextClassifier.DEFAULT_LOG_TAG;
    private final IntentFactory mFallback;

    public TemplateIntentFactory(IntentFactory fallback) {
        mFallback = Preconditions.checkNotNull(fallback);
    }

    /**
     * Returns a list of {@link android.view.textclassifier.TextClassifierImpl.LabeledIntent}
     * that are constructed from the classification result.
     */
    @NonNull
    @Override
    public List<TextClassifierImpl.LabeledIntent> create(
            Context context,
            String text,
            boolean foreignText,
            @Nullable Instant referenceTime,
            @Nullable AnnotatorModel.ClassificationResult classification) {
        if (classification == null) {
            return Collections.emptyList();
        }
        RemoteActionTemplate[] remoteActionTemplates = classification.getRemoteActionTemplates();
        if (ArrayUtils.isEmpty(remoteActionTemplates)) {
            // RemoteActionTemplate is missing, fallback.
            Log.w(TAG, "RemoteActionTemplate is missing, fallback to LegacyIntentFactory.");
            return mFallback.create(context, text, foreignText, referenceTime, classification);
        }
        final List<TextClassifierImpl.LabeledIntent> labeledIntents =
                new ArrayList<>(createFromRemoteActionTemplates(remoteActionTemplates));
        if (foreignText) {
            IntentFactory.insertTranslateAction(labeledIntents, context, text.trim());
        }
        labeledIntents.forEach(
                action -> action.getIntent()
                        .putExtra(TextClassifier.EXTRA_FROM_TEXT_CLASSIFIER, true));
        return labeledIntents;
    }

    private static List<TextClassifierImpl.LabeledIntent> createFromRemoteActionTemplates(
            RemoteActionTemplate[] remoteActionTemplates) {
        final List<TextClassifierImpl.LabeledIntent> labeledIntents = new ArrayList<>();
        for (RemoteActionTemplate remoteActionTemplate : remoteActionTemplates) {
            Intent intent = createIntent(remoteActionTemplate);
            if (intent == null) {
                continue;
            }
            TextClassifierImpl.LabeledIntent
                    labeledIntent = new TextClassifierImpl.LabeledIntent(
                    remoteActionTemplate.title,
                    remoteActionTemplate.description,
                    intent,
                    remoteActionTemplate.requestCode == null
                            ? TextClassifierImpl.LabeledIntent.DEFAULT_REQUEST_CODE
                            : remoteActionTemplate.requestCode
            );
            labeledIntents.add(labeledIntent);
        }
        return labeledIntents;
    }

    @Nullable
    private static Intent createIntent(RemoteActionTemplate remoteActionTemplate) {
        Intent intent = new Intent();
        if (!TextUtils.isEmpty(remoteActionTemplate.packageName)) {
            Log.w(TAG, "A RemoteActionTemplate is skipped as package name is set.");
            return null;
        }
        if (!TextUtils.isEmpty(remoteActionTemplate.action)) {
            intent.setAction(remoteActionTemplate.action);
        }
        Uri data = null;
        if (!TextUtils.isEmpty(remoteActionTemplate.data)) {
            data = Uri.parse(remoteActionTemplate.data);
        }
        if (data != null || !TextUtils.isEmpty(remoteActionTemplate.type)) {
            intent.setDataAndType(data, remoteActionTemplate.type);
        }
        if (remoteActionTemplate.flags != null) {
            intent.setFlags(remoteActionTemplate.flags);
        }
        if (remoteActionTemplate.category != null) {
            for (String category : remoteActionTemplate.category) {
                intent.addCategory(category);
            }
        }
        intent.putExtras(createExtras(remoteActionTemplate.extras));
        return intent;
    }

    private static Bundle createExtras(NamedVariant[] namedVariants) {
        if (namedVariants == null) {
            return Bundle.EMPTY;
        }
        Bundle bundle = new Bundle();
        for (NamedVariant namedVariant : namedVariants) {
            switch (namedVariant.getType()) {
                case NamedVariant.TYPE_INT:
                    bundle.putInt(namedVariant.getName(), namedVariant.getInt());
                    break;
                case NamedVariant.TYPE_LONG:
                    bundle.putLong(namedVariant.getName(), namedVariant.getLong());
                    break;
                case NamedVariant.TYPE_FLOAT:
                    bundle.putFloat(namedVariant.getName(), namedVariant.getFloat());
                    break;
                case NamedVariant.TYPE_DOUBLE:
                    bundle.putDouble(namedVariant.getName(), namedVariant.getDouble());
                    break;
                case NamedVariant.TYPE_BOOL:
                    bundle.putBoolean(namedVariant.getName(), namedVariant.getBool());
                    break;
                case NamedVariant.TYPE_STRING:
                    bundle.putString(namedVariant.getName(), namedVariant.getString());
                    break;
                default:
                    Log.w(TAG,
                            "Unsupported type found in createExtras : " + namedVariant.getType());
            }
        }
        return bundle;
    }
}
