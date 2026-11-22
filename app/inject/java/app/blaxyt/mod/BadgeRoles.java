package app.blaxyt.mod;

/** BlaxyT cosmetic catalogue. Authorization is exclusively a server concern. */
final class BadgeRoles {
    static final String[] IDS={"developer","supporter","ambassador","helper"};
    private BadgeRoles() {}
    static boolean local(String id) {
        for(String role:IDS)if(role.equals(id))return true;
        return false;
    }
    static String sourceLabel(String source) {
        if("margyt".equals(source))return "MargyT";
        if("you".equals(source))return "TikTok You";
        if("tiktokmi".equals(source))return "TikTok Mi";
        return null;
    }
    static boolean imported(String id) {
        return id!=null&&id.matches("import:(margyt|you|tiktokmi):[a-f0-9]{24}");
    }
}
