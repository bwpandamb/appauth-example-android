package com.ef.appauthexample

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatButton
import android.support.v7.widget.AppCompatTextView
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.ImageView
import com.ef.appauthexample.MainApplication.Companion.LOG_TAG
import net.openid.appauth.*
import org.json.JSONException

class MainActivity : AppCompatActivity() {

    private val SHARED_PREFERENCES_NAME = "AuthStatePreference"
    private val AUTH_STATE = "AUTH_STATE"
    private val USED_INTENT = "USED_INTENT"

    var mMainApplication: MainApplication? = null

    // state
    var mAuthState: AuthState? = null

    // views
    var mAuthorize: AppCompatButton? = null
    var mMakeApiCall: AppCompatButton? = null
    var mSignOut: AppCompatButton? = null
    var mGivenName: AppCompatTextView? = null
    var mFamilyName: AppCompatTextView? = null
    var mFullName: AppCompatTextView? = null
    var mProfileView: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mMainApplication = application as MainApplication
        mAuthorize = findViewById<AppCompatButton>(R.id.authorize)
        mMakeApiCall = findViewById<AppCompatButton>(R.id.makeApiCall)
        mSignOut = findViewById<AppCompatButton>(R.id.signOut)
        mGivenName = findViewById<AppCompatTextView>(R.id.givenName)
        mFamilyName = findViewById<AppCompatTextView>(R.id.familyName)
        mFullName = findViewById<AppCompatTextView>(R.id.fullName)
        mProfileView = findViewById<ImageView>(R.id.profileImage)

        enablePostAuthorizationFlows()

        // wire click listeners
        mAuthorize!!.setOnClickListener(AuthorizeListener())
    }

    private fun enablePostAuthorizationFlows() {
        mAuthState = restoreAuthState()
        val authState = mAuthState

        if (authState != null && authState.isAuthorized()) {
            if (mMakeApiCall!!.getVisibility() === View.GONE) {
                mMakeApiCall!!.setVisibility(View.VISIBLE)
                mMakeApiCall!!.setOnClickListener(MakeApiCallListener(this, authState, AuthorizationService(this)))
            }
            if (mSignOut!!.getVisibility() === View.GONE) {
                mSignOut!!.setVisibility(View.VISIBLE)
                mSignOut!!.setOnClickListener(SignOutListener(this))
            }
        } else {
            mMakeApiCall!!.setVisibility(View.GONE)
            mSignOut!!.setVisibility(View.GONE)
        }
    }

    /**
     * Exchanges the code, for the [TokenResponse].
     *
     * @param intent represents the [Intent] from the Custom Tabs or the System Browser.
     */
    private fun handleAuthorizationResponse(intent: Intent) {
        val response = AuthorizationResponse.fromIntent(intent)
        val error = AuthorizationException.fromIntent(intent)
        val authState = AuthState(response, error)

        if (response != null) {
            Log.i(LOG_TAG, String.format("Handled Authorization Response %s ", authState.jsonSerializeString()))
            val service = AuthorizationService(this)
            service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, exception ->
                if (exception != null) {
                    Log.w(LOG_TAG, "Token Exchange failed", exception)
                } else {
                    if (tokenResponse != null) {
                        authState.update(tokenResponse, exception)
                        persistAuthState(authState)
                        Log.i(LOG_TAG, String.format("Token Response [ Access Token: %s, ID Token: %s ]", tokenResponse.accessToken, tokenResponse.idToken))
                    }
                }
            }
        }
    }

    private fun persistAuthState(authState: AuthState) {
        getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
                .putString(AUTH_STATE, authState.jsonSerializeString())
                .commit()
        enablePostAuthorizationFlows()
    }

    private fun clearAuthState() {
        getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(AUTH_STATE)
                .apply()
    }

    private fun restoreAuthState(): AuthState? {
        val jsonString = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(AUTH_STATE, null)
        if (!TextUtils.isEmpty(jsonString)) {
            try {
                return AuthState.jsonDeserialize(jsonString)
            } catch (jsonException: JSONException) {
                // should never happen
            }

        }
        return null
    }

    override fun onNewIntent(intent: Intent) {
        checkIntent(intent)
    }

    private fun checkIntent(intent: Intent?) {
        if (intent != null) {
            val action = intent.action
            when (action) {
                "com.ef.appauthexample.HANDLE_AUTHORIZATION_RESPONSE" -> if (!intent.hasExtra(USED_INTENT)) {
                    handleAuthorizationResponse(intent)
                    intent.putExtra(USED_INTENT, true)
                }
            }// do nothing
        }
    }

    override fun onStart() {
        super.onStart()
        checkIntent(intent)
    }

    /**
     * Kicks off the authorization flow.
     */
    class AuthorizeListener : View.OnClickListener {
        override fun onClick(view: View) {
            val serviceConfiguration = AuthorizationServiceConfiguration(
                    Uri.parse("https://internal-cne1qasso-int-alb-1026644593.cn-north-1.elb.amazonaws.com.cn/connect/authorize"),
                    Uri.parse("https://internal-cne1qasso-int-alb-1026644593.cn-north-1.elb.amazonaws.com.cn/connect/token")
            )

            val request = AuthorizationRequest.Builder(
                    serviceConfiguration,
                    "efpv2.mobile.client",
                    ResponseTypeValues.CODE,
                    Uri.parse("com.ef.appauthexample:/oauth2callback")
            )
                    .setScopes("openid", "efpv2")
                    .build()

            val pendingIntent = PendingIntent.getActivity(
                    view.context,
                    request.hashCode(),
                    Intent("com.ef.appauthexample.HANDLE_AUTHORIZATION_RESPONSE"),
                    0
            )
            AuthorizationService(view.getContext()).performAuthorizationRequest(request, pendingIntent)
        }
    }

    class SignOutListener(private val mMainActivity: MainActivity) : View.OnClickListener {
        override fun onClick(view: View) {
            mMainActivity.mAuthState = null
            mMainActivity.clearAuthState()
            mMainActivity.enablePostAuthorizationFlows()
        }
    }

    class MakeApiCallListener(private val mMainActivity: MainActivity, private val mAuthState: AuthState, private val mAuthorizationService: AuthorizationService) : View.OnClickListener {
        override fun onClick(view: View) {

            // code from the section 'Making API Calls' goes here

        }
    }

}
