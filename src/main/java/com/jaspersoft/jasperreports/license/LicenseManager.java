package com.jaspersoft.jasperreports.license;

import net.sf.jasperreports.engine.JasperReportsContext;

public class LicenseManager {
    public static LicenseManager cachedInstance(JasperReportsContext context) {
        return new LicenseManager();
    }
}
