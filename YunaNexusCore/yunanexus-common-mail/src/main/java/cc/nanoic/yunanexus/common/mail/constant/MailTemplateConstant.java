package cc.nanoic.yunanexus.common.mail.constant;

/**
 * 验证码模板
 * 
 * @param code    验证码
 * @param minutes 过期时间（分钟）
 */
public final class MailTemplateConstant {

    private static final String style = "<style>\r\n" + //
            "  body {\r\n" + //
            "    margin: 0;\r\n" + //
            "    padding: 0;\r\n" + //
            "    font-family: Inter, -apple-system, system-ui, \"Segoe UI\", Helvetica, Arial, sans-serif;\r\n" + //
            "    background-color: #f6f5f4;\r\n" + //
            "  }\r\n" + //
            "  .container {\r\n" + //
            "    width: 100%;\r\n" + //
            "    max-width: 640px;\r\n" + //
            "    margin: 0 auto;\r\n" + //
            "    padding: 24px;\r\n" + //
            "  }\r\n" + //
            "  .card {\r\n" + //
            "    background-color: #ffffff;\r\n" + //
            "    border-radius: 12px;\r\n" + //
            "    border: 1px solid rgba(0,0,0,0.1);\r\n" + //
            "    box-shadow:\r\n" + //
            "      rgba(0,0,0,0.04) 0px 4px 18px,\r\n" + //
            "      rgba(0,0,0,0.027) 0px 2.025px 7.84688px,\r\n" + //
            "      rgba(0,0,0,0.02) 0px 0.8px 2.925px;\r\n" + //
            "    padding: 32px;\r\n" + //
            "    overflow: hidden;\r\n" + //
            "  }\r\n" + //
            "  .header-logo {\r\n" + //
            "    color: #0075de;\r\n" + //
            "    font-size: 18px;\r\n" + //
            "    font-weight: 600;\r\n" + //
            "    margin-bottom: 20px;\r\n" + //
            "    display: flex;\r\n" + //
            "    align-items: center;\r\n" + //
            "  }\r\n" + //
            "  h1 {\r\n" + //
            "    color: rgba(0,0,0,0.95);\r\n" + //
            "    font-size: 26px;\r\n" + //
            "    font-weight: 700;\r\n" + //
            "    letter-spacing: -0.625px;\r\n" + //
            "    line-height: 1.23;\r\n" + //
            "    margin-top: 0;\r\n" + //
            "  }\r\n" + //
            "  p {\r\n" + //
            "    color: #615d59;\r\n" + //
            "    line-height: 1.5;\r\n" + //
            "    font-size: 16px;\r\n" + //
            "  }\r\n" + //
            "  .btn {\r\n" + //
            "    display: inline-block;\r\n" + //
            "    padding: 8px 16px;\r\n" + //
            "    background-color: #0075de;\r\n" + //
            "    color: #ffffff !important;\r\n" + //
            "    text-decoration: none;\r\n" + //
            "    border-radius: 4px;\r\n" + //
            "    border: 1px solid transparent;\r\n" + //
            "    font-size: 15px;\r\n" + //
            "    font-weight: 600;\r\n" + //
            "    margin-top: 16px;\r\n" + //
            "    transition: background-color 0.2s;\r\n" + //
            "  }\r\n" + //
            "  .footer {\r\n" + //
            "    text-align: center;\r\n" + //
            "    margin-top: 24px;\r\n" + //
            "    color: #a39e98;\r\n" + //
            "    font-size: 12px;\r\n" + //
            "  }\r\n" + //
            "  .badge {\r\n" + //
            "    padding: 4px 8px;\r\n" + //
            "    border-radius: 9999px;\r\n" + //
            "    font-size: 12px;\r\n" + //
            "    font-weight: 600;\r\n" + //
            "  }\r\n" + //
            "</style>";

    public static final String VERIFICATION = style + "<div class=\"container\">\r\n" + //
            "  <div class=\"card\">\r\n" + //
            "    <div class=\"header-logo\">\r\n" + //
            "      <span\r\n" + //
            "        style=\"\r\n" + //
            "          background: linear-gradient(135deg, #0075de 0%, #62aef0 100%);\r\n" + //
            "          width: 14px;\r\n" + //
            "          height: 14px;\r\n" + //
            "          border-radius: 4px;\r\n" + //
            "          margin-right: 10px;\r\n" + //
            "        \"\r\n" + //
            "      ></span>\r\n" + //
            "      YunaNexusMailer\r\n" + //
            "    </div>\r\n" + //
            "    <h1>验证您的身份</h1>\r\n" + //
            "    <p>\r\n" + //
            "      您正在进行敏感操作，请使用下方的验证码完成身份确认。该验证码将在\r\n" + //
            "      ${minutes} 分钟后失效。\r\n" + //
            "    </p>\r\n" + //
            "\r\n" + //
            "    <div\r\n" + //
            "      style=\"\r\n" + //
            "        background: #f2f9ff;\r\n" + //
            "        border: 1px solid #d7ebff;\r\n" + //
            "        border-radius: 12px;\r\n" + //
            "        padding: 24px;\r\n" + //
            "        text-align: center;\r\n" + //
            "        margin: 28px 0;\r\n" + //
            "      \"\r\n" + //
            "    >\r\n" + //
            "      <span\r\n" + //
            "        style=\"\r\n" + //
            "          font-size: 40px;\r\n" + //
            "          font-weight: 700;\r\n" + //
            "          color: #0075de;\r\n" + //
            "          letter-spacing: 10px;\r\n" + //
            "          text-shadow: 0 1px 0 rgba(255,255,255,0.8);\r\n" + //
            "        \"\r\n" + //
            "        >${code}</span\r\n" + //
            "      >\r\n" + //
            "    </div>\r\n" + //
            "\r\n" + //
            "    <p style=\"font-size: 13px; color: #a39e98\">\r\n" + //
            "      如果您没有请求此操作，请忽略此邮件，您的账号目前很安全。\r\n" + //
            "    </p>\r\n" + //
            "  </div>\r\n" + //
            "  <div class=\"footer\">© 2026 YunaNexus</div>\r\n" + //
            "</div>";

    /**
     * 通知模板
     * 
     * @param serviceName 服务名称
     * @param title       通知标题
     * @param content     通知内容
     * @param actionUrl   具体内容链接
     */
    public static final String NOTIFICATION = style + "<div class=\"container\">\r\n" + //
            "  <div class=\"card\" style=\"border-top: 4px solid #0075de\">\r\n" + //
            "    <div class=\"header-logo\"><span class=\"badge\" style=\"background:#f2f9ff;color:#097fe8;margin-right:8px;\">通知</span>${serviceName}</div>\r\n" + //
            "    <h1>${title}</h1>\r\n" + //
            "\r\n" + //
            "    <div\r\n" + //
            "      style=\"border-left: 4px solid #0075de; background:#f8fbff; border-radius:8px; padding:14px 16px; margin: 20px 0\"\r\n" + //
            "    >\r\n" + //
            "      <p style=\"margin: 0; font-weight: 600; color: rgba(0,0,0,0.95)\">${content}</p>\r\n" + //
            "    </div>\r\n" + //
            "\r\n" + //
            "    <a href=\"${actionUrl}\" class=\"btn\">查看详细信息</a>\r\n" + //
            "\r\n" + //
            "    <p\r\n" + //
            "      style=\"margin-top: 32px; border-top: 1px solid #f0f0f0; padding-top: 16px\"\r\n" + //
            "    >\r\n" + //
            "      需要帮助？请访问我们的\r\n" + //
            "      <!-- TODO: 补充支持中心链接 -->\r\n" + //
            "      <a href=\"#\" style=\"color: #0078d4; text-decoration: none\">支持中心</a>。\r\n" + //
            "    </p>\r\n" + //
            "  </div>\r\n" + //
            "</div>";

    /**
     * 告警模板
     * 
     * @param serviceName  服务名称
     * @param nodeName     告警节点
     * @param errorType    错误类型
     * @param dashboardUrl 控制台地址
     */
    public static final String ALERT = style + "<div class=\"container\">\r\n" + //
            "  <div class=\"card\" style=\"border-top: 4px solid #dd5b00; background:#fffdfb;\">\r\n" + //
            "    <div style=\"display: flex; align-items: center; margin-bottom: 20px\">\r\n" + //
            "      <span\r\n" + //
            "        style=\"\r\n" + //
            "          background: #fff1e8;\r\n" + //
            "          color: #dd5b00;\r\n" + //
            "          padding: 6px 12px;\r\n" + //
            "          border-radius: 9999px;\r\n" + //
            "          font-size: 12px;\r\n" + //
            "          font-weight: bold;\r\n" + //
            "        \"\r\n" + //
            "      >\r\n" + //
            "        ${serviceName} - 系统告警\r\n" + //
            "      </span>\r\n" + //
            "    </div>\r\n" + //
            "    <h1 style=\"color: #d83b01\">系统运行异常告警</h1>\r\n" + //
            "    <p>监控系统检测到以下资源超限或运行故障，请立即处理：</p>\r\n" + //
            "\r\n" + //
            "    <table style=\"width: 100%; border-collapse: collapse; margin: 16px 0\">\r\n" + //
            "      <tr style=\"background: #fff7f2\">\r\n" + //
            "        <td\r\n" + //
            "          style=\"\r\n" + //
            "            padding: 12px;\r\n" + //
            "            font-size: 13px;\r\n" + //
            "            color: #1b1b1b;\r\n" + //
            "            border-radius: 4px 0 0 4px;\r\n" + //
            "          \"\r\n" + //
            "        >\r\n" + //
            "          <strong>告警节点:</strong>\r\n" + //
            "        </td>\r\n" + //
            "        <td\r\n" + //
            "          style=\"\r\n" + //
            "            padding: 12px;\r\n" + //
            "            font-size: 13px;\r\n" + //
            "            color: #d83b01;\r\n" + //
            "            border-radius: 0 4px 4px 0;\r\n" + //
            "            text-align: right;\r\n" + //
            "          \"\r\n" + //
            "        >\r\n" + //
            "          ${nodeName}\r\n" + //
            "        </td>\r\n" + //
            "      </tr>\r\n" + //
            "      <tr>\r\n" + //
            "        <td style=\"padding: 12px; font-size: 13px; color: #5d5d5d\">\r\n" + //
            "          错误类型:\r\n" + //
            "        </td>\r\n" + //
            "        <td\r\n" + //
            "          style=\"\r\n" + //
            "            padding: 12px;\r\n" + //
            "            font-size: 13px;\r\n" + //
            "            color: #1b1b1b;\r\n" + //
            "            text-align: right;\r\n" + //
            "          \"\r\n" + //
            "        >\r\n" + //
            "          ${errorType}\r\n" + //
            "        </td>\r\n" + //
            "      </tr>\r\n" + //
            "    </table>\r\n" + //
            "\r\n" + //
            "    <a href=\"${dashboardUrl}\" class=\"btn\" style=\"background-color: #dd5b00\"\r\n" + //
            "      >进入管理控制台</a\r\n" + //
            "    >\r\n" + //
            "  </div>\r\n" + //
            "</div>";
}
