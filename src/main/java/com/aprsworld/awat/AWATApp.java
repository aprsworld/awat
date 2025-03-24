package com.aprsworld.awat;

import android.app.*;
import android.content.Context;
import org.acra.*;
import org.acra.annotation.*;
import org.acra.data.StringFormat;

@AcraCore(buildConfigClass = BuildConfig.class,
        reportFormat = StringFormat.JSON)
//@AcraMailSender(mailTo = "david@aprsworld.com")
@AcraLimiter
public class AWATApp extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);

        // The following line triggers the initialization of ACRA
        ACRA.init(this);
    }
}
