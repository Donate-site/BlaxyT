package app.blaxyt.mod;

import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

/** BlaxyT telephony adapter. Public signatures are the bytecode hook contract. */
public final class Region {
    private Region() {}

    private enum TextRoute {
        SIM_COUNTRY("sim_country", BlaxyRuntime.ISO),
        NETWORK_COUNTRY("network_country", BlaxyRuntime.ISO),
        SIM_OPERATOR("sim_operator", BlaxyRuntime.MCCMNC),
        NETWORK_OPERATOR("network_operator", BlaxyRuntime.MCCMNC),
        SIM_NAME("sim_operator_name", BlaxyRuntime.CARRIER),
        NETWORK_NAME("network_operator_name", BlaxyRuntime.CARRIER);
        final String pluginKey;
        final int countryField;
        TextRoute(String key, int field) { pluginKey=key; countryField=field; }
        String nativeValue(TelephonyManager phone) {
            if (phone==null) return "";
            switch(this) {
                case SIM_COUNTRY: return phone.getSimCountryIso();
                case NETWORK_COUNTRY: return phone.getNetworkCountryIso();
                case SIM_OPERATOR: return phone.getSimOperator();
                case NETWORK_OPERATOR: return phone.getNetworkOperator();
                case SIM_NAME: return phone.getSimOperatorName();
                default: return phone.getNetworkOperatorName();
            }
        }
    }

    private static String resolve(TextRoute route, TelephonyManager phone) {
        String value;
        if (BlaxyRuntime.active()) value=BlaxyRuntime.current()[route.countryField];
        else value=route.nativeValue(phone);
        return Plugins.region(route.pluginKey,value);
    }
    public static String getSimCountryIso(TelephonyManager p) { return resolve(TextRoute.SIM_COUNTRY,p); }
    public static String getNetworkCountryIso(TelephonyManager p) { return resolve(TextRoute.NETWORK_COUNTRY,p); }
    public static String getSimOperator(TelephonyManager p) { return resolve(TextRoute.SIM_OPERATOR,p); }
    public static String getNetworkOperator(TelephonyManager p) { return resolve(TextRoute.NETWORK_OPERATOR,p); }
    public static String getSimOperatorName(TelephonyManager p) { return resolve(TextRoute.SIM_NAME,p); }
    public static String getNetworkOperatorName(TelephonyManager p) { return resolve(TextRoute.NETWORK_NAME,p); }

    private static boolean nativeHardware(TelephonyManager phone) {
        if (!BlaxyRuntime.active()) return true;
        if (phone==null) return false;
        try { return phone.hasIccCard() || phone.getSimState()==TelephonyManager.SIM_STATE_READY; }
        catch (Throwable unavailable) { return false; }
    }
    private enum Measure { STATE, SLOT_STATE, PHONE_COUNT, MODEM_COUNT, PHONE_TYPE }
    private static int measure(TelephonyManager phone, Measure route, int slot) {
        if (!nativeHardware(phone)) {
            switch(route) {
                case STATE: return TelephonyManager.SIM_STATE_READY;
                case SLOT_STATE: return slot==0 ? TelephonyManager.SIM_STATE_READY : TelephonyManager.SIM_STATE_UNKNOWN;
                case PHONE_TYPE: return TelephonyManager.PHONE_TYPE_GSM;
                default: return 1;
            }
        }
        if (phone==null) return 0;
        switch(route) {
            case STATE: return phone.getSimState();
            case SLOT_STATE: return phone.getSimState(slot);
            case PHONE_COUNT: return phone.getPhoneCount();
            case MODEM_COUNT: return phone.getActiveModemCount();
            default: return phone.getPhoneType();
        }
    }
    public static int getSimState(TelephonyManager p) { return measure(p,Measure.STATE,0); }
    public static int getSimState(TelephonyManager p,int slot) { return measure(p,Measure.SLOT_STATE,slot); }
    public static int getPhoneCount(TelephonyManager p) { return measure(p,Measure.PHONE_COUNT,0); }
    public static int getActiveModemCount(TelephonyManager p) { return measure(p,Measure.MODEM_COUNT,0); }
    public static int getPhoneType(TelephonyManager p) { return measure(p,Measure.PHONE_TYPE,0); }
    public static boolean hasIccCard(TelephonyManager p) {
        return !nativeHardware(p) || (p!=null && p.hasIccCard());
    }
    public static boolean isNetworkRoaming(TelephonyManager p) {
        return !BlaxyRuntime.active() && p!=null && p.isNetworkRoaming();
    }
    public static int getSimCarrierId(TelephonyManager p) {
        if (BlaxyRuntime.active()) return TelephonyManager.UNKNOWN_CARRIER_ID;
        return p==null ? -1 : p.getSimCarrierId();
    }
    public static int getActiveSubscriptionInfoCount(SubscriptionManager subscriptions) {
        if (BlaxyRuntime.active()) return 1;
        return subscriptions==null ? 0 : subscriptions.getActiveSubscriptionInfoCount();
    }
    private static int defaultSubscription(int service) {
        if (BlaxyRuntime.active()) return 1;
        switch(service) {
            case 0: return SubscriptionManager.getDefaultDataSubscriptionId();
            case 1: return SubscriptionManager.getDefaultVoiceSubscriptionId();
            case 2: return SubscriptionManager.getDefaultSmsSubscriptionId();
            default: return SubscriptionManager.getActiveDataSubscriptionId();
        }
    }
    public static int getDefaultDataSubscriptionId() { return defaultSubscription(0); }
    public static int getDefaultVoiceSubscriptionId() { return defaultSubscription(1); }
    public static int getDefaultSmsSubscriptionId() { return defaultSubscription(2); }
    public static int getActiveDataSubscriptionId() { return defaultSubscription(3); }
}
