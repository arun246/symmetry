package io.symmetry.analytics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only aggregates for the dashboards. The UI never computes business totals
 * from a page of rows; it asks for a number that the database already grouped.
 */
@Service
public class AnalyticsService {

    private final JdbcTemplate jdbc;

    public AnalyticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> overview(UUID orgId) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("contracts", countContracts(orgId));
        summary.put("acceptances", countAcceptances(orgId));
        summary.put("verified", countVerifiedContracts(orgId));
        summary.put("workers", count("select count(*) from app_user where role = 'WORKER'"));
        summary.put("organizations", count("select count(*) from organization"));
        summary.put("pendingConfigRequests",
                count("select count(*) from config_request where status = 'PENDING'"));
        summary.put("auditEvents", count("select count(*) from audit_log"));
        return summary;
    }

    public List<MetricPoint> contractsByState(UUID orgId) {
        String sql = "select lifecycle_state as label, count(*) as value from contract"
                + (orgId == null ? "" : " where org_id = ?")
                + " group by lifecycle_state order by value desc";
        return orgId == null ? points(sql) : points(sql, orgId);
    }

    public List<MetricPoint> acceptancesByDay(UUID orgId, int days) {
        int window = Math.min(Math.max(days, 1), 365);
        List<Object> args = new ArrayList<>();
        args.add(window);
        String join = orgId == null ? "" : " join contract c on c.id = a.contract_id";
        String filter = orgId == null ? "" : " and c.org_id = ?";
        if (orgId != null) {
            args.add(orgId);
        }
        String sql = "select to_char(a.accepted_at::date, 'YYYY-MM-DD') as label, count(*) as value"
                + " from acceptance a" + join
                + " where a.accepted_at >= now() - make_interval(days => ?)" + filter
                + " group by 1 order by 1";
        return points(sql, args.toArray());
    }

    public List<MetricPoint> topModules(UUID orgId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 20);
        List<Object> args = new ArrayList<>();
        if (orgId != null) {
            args.add(orgId);
        }
        args.add(capped);
        String sql = "select module_id as label, count(*) as value from contract"
                + (orgId == null ? "" : " where org_id = ?")
                + " group by module_id order by value desc limit ?";
        return points(sql, args.toArray());
    }

    public List<MetricPoint> verification(UUID orgId) {
        String join = orgId == null ? "" : " join contract c on c.id = a.contract_id";
        String filter = orgId == null ? "" : " where c.org_id = ?";
        String sql = "select case when a.verified then 'verified' else 'failed' end as label,"
                + " count(*) as value from acceptance a" + join + filter + " group by 1";
        return orgId == null ? points(sql) : points(sql, orgId);
    }

    public List<MetricPoint> funnel(UUID orgId) {
        List<MetricPoint> points = new ArrayList<>();
        points.add(new MetricPoint("Published", countContracts(orgId)));
        points.add(new MetricPoint("Accepted", countDistinctAccepted(orgId, false)));
        points.add(new MetricPoint("Verified", countDistinctAccepted(orgId, true)));
        return points;
    }

    public long countContracts(UUID orgId) {
        return orgId == null
                ? count("select count(*) from contract")
                : count("select count(*) from contract where org_id = ?", orgId);
    }

    public long countAcceptances(UUID orgId) {
        return orgId == null
                ? count("select count(*) from acceptance")
                : count("select count(*) from acceptance a join contract c on c.id = a.contract_id"
                        + " where c.org_id = ?", orgId);
    }

    public long countVerifiedContracts(UUID orgId) {
        return countDistinctAccepted(orgId, true);
    }

    private long countDistinctAccepted(UUID orgId, boolean onlyVerified) {
        String join = orgId == null ? "" : " join contract c on c.id = a.contract_id";
        StringBuilder sql = new StringBuilder(
                "select count(distinct a.contract_id) from acceptance a").append(join).append(" where 1 = 1");
        List<Object> args = new ArrayList<>();
        if (orgId != null) {
            args.add(orgId);
            sql.append(" and c.org_id = ?");
        }
        if (onlyVerified) {
            sql.append(" and a.verified = true");
        }
        return count(sql.toString(), args.toArray());
    }

    private List<MetricPoint> points(String sql, Object... args) {
        return jdbc.query(sql, (rs, row) -> new MetricPoint(rs.getString("label"), rs.getLong("value")), args);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }
}
