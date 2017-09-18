package dk.netarkivet.heritrix3.monitor.resources;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.antiaction.common.filter.Caching;
import com.antiaction.common.templateengine.TemplateBuilderFactory;

import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.harvester.HarvesterSettings;
import dk.netarkivet.harvester.datamodel.HarvestChannel;
import dk.netarkivet.heritrix3.monitor.Heritrix3JobMonitor;
import dk.netarkivet.heritrix3.monitor.Heritrix3JobMonitorThread;
import dk.netarkivet.heritrix3.monitor.HistoryServlet;
import dk.netarkivet.heritrix3.monitor.NASEnvironment;
import dk.netarkivet.heritrix3.monitor.NASUser;
import dk.netarkivet.heritrix3.monitor.ResourceAbstract;
import dk.netarkivet.heritrix3.monitor.ResourceManagerAbstract;

public class IndexResource implements ResourceAbstract {

    private NASEnvironment environment;

    protected int R_INDEX = -1;

    @Override
    public void resources_init(NASEnvironment environment) {
        this.environment = environment;
    }

    @Override
    public void resources_add(ResourceManagerAbstract resourceManager) {
        R_INDEX = resourceManager.resource_add(this, "/", false);
    }

    @Override
    public void resource_service(ServletContext servletContext, NASUser nas_user, HttpServletRequest req, HttpServletResponse resp, int resource_id, List<Integer> numerics, String pathInfo) throws IOException {
        if (NASEnvironment.contextPath == null) {
            NASEnvironment.contextPath = req.getContextPath();
        }
        if (NASEnvironment.servicePath == null) {
            NASEnvironment.servicePath = req.getContextPath() + req.getServletPath() + "/";
        }
        String method = req.getMethod().toUpperCase();
        if (resource_id == R_INDEX) {
            if ("GET".equals(method)) {
                index(req, resp, numerics);
            }
        }
    }

    public static class HarvestChannelStructure {
        public HarvestChannel hc;
        public List<Heritrix3JobMonitor> h3JobList = new ArrayList<Heritrix3JobMonitor>();
        public HarvestChannelStructure(HarvestChannel hc) {
            this.hc = hc;
        }
    }

    public void index(HttpServletRequest req, HttpServletResponse resp, List<Integer> numerics) throws IOException {
        Locale locale = resp.getLocale();
        resp.setContentType("text/html; charset=UTF-8");
        ServletOutputStream out = resp.getOutputStream();
        Caching.caching_disable_headers(resp);

        TemplateBuilderFactory<MasterTemplateBuilder> masterTplBuilderFactory = TemplateBuilderFactory.getInstance(environment.templateMaster, "master.tpl", "UTF-8", MasterTemplateBuilder.class);
        MasterTemplateBuilder masterTplBuilder = masterTplBuilderFactory.getTemplateBuilder();

        StringBuilder sb = new StringBuilder();

        List<Heritrix3JobMonitor> h3JobsList = environment.h3JobMonitorThread.getRunningH3Jobs();
        Heritrix3JobMonitor h3Job;

        // Restart H3 job monitor thread.
        String action = req.getParameter("action");
        if (action != null && action.length() > 0) {
            if ("restart".equalsIgnoreCase(action)) {
                if (environment.h3JobMonitorThread.thread != null) {
                    synchronized (environment.h3JobMonitorThread.thread) {
                        if (!environment.h3JobMonitorThread.thread.isAlive()) {
                            // The h3JobMonitorThread is dead. Restart it.
                            Heritrix3JobMonitorThread newH3JobMonitor = new Heritrix3JobMonitorThread(environment);
                            newH3JobMonitor.start();
                            environment.h3JobMonitorThread = newH3JobMonitor; 
                        }
                    }
                }
            }
        }

        sb.append("<a href=\"");
        sb.append(NASEnvironment.servicePath);
        sb.append("config/");
        sb.append("\" class=\"btn btn-default\">");
        sb.append(environment.I18N.getString(locale, "configure"));
        sb.append("</a>");
        sb.append("<br />\n");
        sb.append("<br />\n");

        // Check if H3 job monitor thread is still running.
        if (environment.h3JobMonitorThread.thread != null) {
            synchronized (environment.h3JobMonitorThread.thread) {
                if (!environment.h3JobMonitorThread.thread.isAlive()) {
                    sb.append("The H3 job monitor thread is not running anymore. ");
                    sb.append("<a href=\"?action=restart");
                    sb.append("\"");
                    sb.append(" class=\"btn btn-default\">");
                    sb.append("Restart");
                    sb.append("</a>");
                    sb.append("<br />\n");
                    sb.append("<pre>");
                    sb.append("Stacktrace[]:");
                    HistoryServlet.throwable_stacktrace_dump(environment.h3JobMonitorThread.throwable, sb);
                    sb.append("</pre>");
                    sb.append("<br />\n");
                    sb.append("<br />\n");
                }
            }
        }

        List<HarvestChannelStructure> hcList = new ArrayList<HarvestChannelStructure>();
        Map<String, HarvestChannelStructure> hcMap = new HashMap<String, HarvestChannelStructure>();

        Iterator<HarvestChannel> hcIter = Heritrix3JobMonitorThread.harvestChannelDAO.getAll(true);
        HarvestChannel hc;
        HarvestChannelStructure hcs;
        while (hcIter.hasNext()) {
            hc = hcIter.next();
            hcs = new HarvestChannelStructure(hc);
            hcList.add(hcs);
            hcMap.put(hc.getName(), hcs);
        }

        Iterator<Heritrix3JobMonitor> j3JobIter = h3JobsList.iterator();
        while (j3JobIter.hasNext()) {
            h3Job = j3JobIter.next();
            if (!h3Job.bInitialized) {
                h3Job.init();
            }
            hcs = hcMap.get(h3Job.job.getChannel());
            hcs.h3JobList.add(h3Job);
        }

        for (int i=0; i<hcList.size(); ++i) {
            hcs = hcList.get(i);
            sb.append("<h5>");
            sb.append(hcs.hc.getName());
            if (hcs.hc.isDefault()) {
                sb.append("*");
            }
            sb.append("&nbsp;");
            sb.append("(");
            sb.append(environment.I18N.getString(locale, "harvest.channel.type"));
            sb.append(": ");
            if (hcs.hc.isSnapshot()) {
                sb.append(environment.I18N.getString(locale, "harvest.channel.type.broad"));
            } else {
                sb.append(environment.I18N.getString(locale, "harvest.channel.type.focused"));
            }
            sb.append(")");
            sb.append("</h5>\n");
            if (hcs.h3JobList.size() > 0) {
                for (int j=0; j<hcs.h3JobList.size(); ++j) {
                    h3Job = hcs.h3JobList.get(j);
                    if (j > 0) {
                        sb.append("&nbsp;");
                    }
                    sb.append("<a href=\"");
                    sb.append(NASEnvironment.servicePath);
                    sb.append("job/");
                    sb.append(h3Job.jobId);
                    sb.append("/");
                    sb.append("\" class=\"btn btn-default\">");
                    sb.append("Job ");
                    sb.append(h3Job.jobId);
                    long lines = (h3Job.idxFile.length() / 8) - 1;
                    if (lines > 0) {
                        sb.append(" (");
                        sb.append(lines);
                        sb.append(")");
                    }
                    sb.append("</a>\n");
                }
            } else {
                sb.append("<p>");
                sb.append(environment.I18N.getString(locale, "running.jobs.monitor.not.on.this.channel"));
                sb.append("</p>\n");
            }
        }

        masterTplBuilder.insertContent("H3 Remote Access", "", environment.generateLanguageLinks(locale), "H3 Remote Access", sb.toString(),
        		"<meta http-equiv=\"refresh\" content=\""+Settings.get(HarvesterSettings.HARVEST_MONITOR_REFRESH_INTERVAL)+"\"/>\n").write(out);

        out.flush();
        out.close();
    }

}