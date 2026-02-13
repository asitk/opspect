DROP KEYSPACE IF EXISTS nuvidata;
CREATE KEYSPACE nuvidata WITH REPLICATION =   {'class':'SimpleStrategy', 'replication_factor' : 1};
USE nuvidata;
DROP TABLE IF EXISTS customer_map;
CREATE TABLE customer_map(
    customer_id ascii,
    name ascii,
    description ascii,
    active boolean,
    ts timestamp,
    PRIMARY KEY(customer_id, ts, name)
)
WITH CLUSTERING ORDER BY (ts DESC);
CREATE INDEX name_cm_idx ON customer_map(name);
CREATE INDEX active_cm_idx  ON customer_map(active);

INSERT INTO customer_map(customer_id, name, description, active, ts)
VALUES('1234abcd', 'ABC Corportation', 'International Fright Company', false, '2016-5-6 15:00:00.00');
INSERT INTO customer_map(customer_id, name, description, active, ts) 
VALUES('1234abcd', 'ABC Corportation', 'International Fright Company', true, '2016-5-7 15:00:00.00');

DROP TABLE IF EXISTS deployment_map;
CREATE TABLE deployment_map(
    customer_id ascii,
    deployment_id ascii,
    name ascii,
    description ascii,
    active boolean,
    ts timestamp,
    PRIMARY KEY((customer_id,deployment_id), ts)
    )
WITH CLUSTERING ORDER BY (ts DESC);
CREATE INDEX active_dm_idx  ON deployment_map(active);
INSERT INTO deployment_map(customer_id, deployment_id, name, description, active, ts) 
VALUES('1234abcd', 'ec2-dc-01', 'EC2 DC-01', 'DataCenter-01 on EC2', false, '2016-5-6 15:00:00.00');
INSERT INTO deployment_map(customer_id, deployment_id, name, description, active, ts) 
VALUES('1234abcd', 'ec2-dc-01', 'EC2 DC-01', 'DataCenter-01 on EC2', true, '2016-5-7 15:00:00.00');

DROP TABLE IF EXISTS cluster_map;
CREATE TABLE cluster_map(
    customer_id ascii,
    deployment_id ascii,
    cluster_id ascii,
    name ascii,
    description ascii,
    role ascii,
    active boolean,
    replicated boolean,
    ts timestamp,
    PRIMARY KEY((customer_id,deployment_id, cluster_id), ts)
    )
WITH CLUSTERING ORDER BY (ts DESC);
CREATE INDEX active_clm_idx  ON cluster_map(active);
CREATE INDEX replicated_clm_idx  ON cluster_map(replicated);
INSERT INTO cluster_map(customer_id, deployment_id, cluster_id, name, description, role, active, replicated, ts)
VALUES('1234abcd', 'ec2-dc-01', 'myappcluster', 'MY-APP-CLUSTER', 'Apache web cluster', 'appserver', false, false, '2016-5-6 15:00:00.00');
INSERT INTO cluster_map(customer_id, deployment_id, cluster_id, name, description, role, active, replicated, ts)
VALUES('1234abcd', 'ec2-dc-01', 'myappcluster', 'MY-APP-CLUSTER', 'Apache web cluster', 'appserver', true, true, '2016-5-7 15:00:00.00');

DROP TABLE IF EXISTS node_map;
CREATE TABLE node_map(
    customer_id ascii,
    deployment_id ascii,
    cluster_id ascii,
    host_ip ascii,
    host_name ascii,
    scope ascii,
    active boolean,
    ts timestamp,
    PRIMARY KEY((customer_id, deployment_id, cluster_id), host_ip, ts)
    )    
WITH CLUSTERING ORDER BY (host_ip ASC, ts DESC);

CREATE INDEX host_ip_nm_idx  ON node_map(host_ip);
CREATE INDEX host_name_nm_idx  ON node_map(host_name);
CREATE INDEX cluster_id_nm_idx  ON node_map(cluster_id);
CREATE INDEX scope_nm_idx  ON node_map(scope);
CREATE INDEX active_nm_idx  ON node_map(active);


INSERT INTO node_map(customer_id, deployment_id, cluster_id, host_ip, host_name, scope, active, ts)  
VALUES ('1234abcd', 'ec2-dc-01', 'myappcluster', '192.168.6.21', 'ak-ubuntu', 'stateless', false, '2016-05-06 15:00:00.00'); 
INSERT INTO node_map(customer_id, deployment_id, cluster_id, host_ip, host_name, scope, active, ts)  
VALUES ('1234abcd',  'ec2-dc-01', 'myappcluster', '192.168.6.21', 'ak-ubuntu', 'stateless', true, '2016-05-07 15:00:00.00'); 


DROP TABLE IF EXISTS metrics_stats;
CREATE TABLE metrics_stats(
    ts timestamp,
    uhash ascii,
    customer_id ascii,
    deployment_id ascii,
    cluster_id ascii,
    target ascii,
    host_ip ascii,
    host_name ascii,
    plugin ascii,
    classification ascii,
    high_water_mark double,
    final_score double,
    is_non_numeric boolean,
    val_stats ascii,
    vel_stats ascii,
    val_str ascii,
    tags ascii,
    PRIMARY KEY((customer_id, deployment_id), ts, uhash)
);
CREATE INDEX host_ip_ms_idx  ON metrics_stats(host_ip);
CREATE INDEX host_name_ms_idx  ON metrics_stats(host_name);
CREATE INDEX cluster_id_ms_idx  ON metrics_stats(cluster_id);
CREATE INDEX plugin_ms_idx  ON metrics_stats(plugin);
CREATE INDEX classification_ms_idx  ON metrics_stats(classification);
CREATE INDEX target_ms_idx  ON metrics_stats(target);
CREATE INDEX final_score_ms_idx ON metrics_stats(final_score);

DROP TABLE IF EXISTS summary_stats_hourly;
CREATE TABLE summary_stats_hourly(
    ts timestamp,
    uhash ascii,
    min_of_hour int,
    customer_id ascii,
    deployment_id ascii,
    cluster_id ascii,
    host_ip ascii,
    host_name ascii,
    thermal int,
    thermal_stats ascii,
    thermal_reason ascii,
    thermal_summary ascii,
    stats ascii,
    connections ascii,
    services ascii,
    PRIMARY KEY((customer_id, deployment_id), ts, uhash)
)
WITH CLUSTERING ORDER BY (ts DESC);
CREATE INDEX thermal_ssh_idx on summary_stats_hourly(thermal);
CREATE INDEX host_ip_ssh_idx  ON summary_stats_hourly(host_ip);
CREATE INDEX host_name_ssh_idx  ON summary_stats_hourly(host_name);
CREATE INDEX cluster_id_ssh_idx  ON summary_stats_hourly(cluster_id);

DROP TABLE IF EXISTS summary_stats_minute;
CREATE TABLE summary_stats_minute(
    ts timestamp,
    uhash ascii,
    min_of_hour int,
    customer_id ascii,
    deployment_id ascii,
    cluster_id ascii,
    host_ip ascii,
    host_name ascii,
    thermal int,
    thermal_stats ascii,
    thermal_reason ascii,
    thermal_summary ascii,
    stats ascii,
    connections ascii,
    services ascii,
    PRIMARY KEY((customer_id, deployment_id, min_of_hour),ts, uhash)
)
WITH CLUSTERING ORDER BY (ts DESC);
CREATE INDEX thermal_ssm_idx on summary_stats_minute(thermal);
CREATE INDEX host_ip_ssm_idx  ON summary_stats_minute(host_ip);
CREATE INDEX host_name_ssm_idx  ON summary_stats_minute(host_name);
CREATE INDEX cluster_id_ssm_idx  ON summary_stats_minute(cluster_id);

DROP TABLE IF EXISTS service_map;
CREATE TABLE service_map(
    ts timestamp,
    uhash ascii,
    customer_id ascii,
    deployment_id ascii,
    cluster_id ascii,
    name ascii,
    svc int,
    ipver int,
    proto int,
    port int,
    interface ascii,
    PRIMARY KEY((customer_id, deployment_id), uhash)
);
CREATE INDEX cluster_sm_idx ON service_map(cluster_id);
CREATE INDEX name_sm_idx ON service_map(name);
CREATE INDEX svc_sm_idx ON service_map(svc);
CREATE INDEX ipver_sm_idx ON service_map(ipver);
CREATE INDEX proto_sm_idx ON service_map(proto);
CREATE INDEX port_sm_idx ON service_map(port);

DROP TABLE IF EXISTS service_stats_counter;
CREATE TABLE service_stats_counter(
    ts timestamp,
    to_info ascii,
    from_info ascii,
    svc_info ascii,
    avg_rsp_size_stats ascii,
    avg_ttfb_stats ascii,
    avg_ttlb_stats ascii,
    max_ttfb_stats ascii,
    max_ttlb_stats ascii,
    recv_bytes_stats ascii,
    sent_bytes_stats ascii,
    request_count_stats ascii,
    error_count_stats ascii,
    duration_stats ascii,
    PRIMARY KEY(to_info)
);

DROP TABLE IF EXISTS anomaly_stats;
CREATE TABLE anomaly_stats(
    ts timestamp,
    duration int,
    uhash ascii,
    customer_id ascii,
    deployment_id ascii,
    cluster_id ascii,
    host_ip ascii,
    host_name ascii,
    target ascii,
    plugin ascii,
    classification ascii,
    is_non_numeric boolean,
    class_type ascii,
    sub_type ascii,
    thermal int,
    sc ascii,
    tags ascii,
    PRIMARY KEY((customer_id, deployment_id), ts, uhash)
);
CREATE INDEX thermal_a_idx on anomaly_stats(thermal);
CREATE INDEX duration_a_idx on anomaly_stats(duration);
CREATE INDEX cluster_id_a_idx on anomaly_stats(cluster_id);
CREATE INDEX host_ip_a_idx on anomaly_stats(host_ip);
CREATE INDEX host_name_a_idx on anomaly_stats(host_name);
CREATE INDEX target_a_idx on anomaly_stats(target);
CREATE INDEX plugin_a_idx on anomaly_stats(plugin);
CREATE INDEX classification_a_idx on anomaly_stats(classification);
CREATE INDEX is_non_numeric_a_idx on anomaly_stats(is_non_numeric);
CREATE INDEX class_type_a_idx on anomaly_stats(class_type);
CREATE INDEX sub_type_a_idx on anomaly_stats(sub_type);


DROP TABLE IF EXISTS connection_stats;
CREATE TABLE connection_stats(
    to_customer_id  ascii,
    to_deployment_id ascii,
    to_cluster_id ascii,
    to_host_ip ascii,
    to_host_name ascii,
    to_scope ascii,
    to_active boolean,
    to_last_modified int,
    from_customer_id  ascii,
    from_deployment_id ascii,
    from_cluster_id ascii,
    from_host_ip ascii,
    from_host_name ascii,
    from_scope ascii,
    from_active boolean,
    from_last_modified int,
    svc_info_cluster_id ascii,
    svc_info_name ascii,
    svc_info_svc int,
    svc_info_ipver int,
    svc_info_port int,
    svc_info_proto int,
    svc_info_interface ascii,
    avg_rsp_size int,
    avg_ttfb int,
    avg_ttlb int,
    max_ttfb int,
    max_ttlb int,
    error_count int,
    request_count int,
    sent_bytes int,
    recv_bytes int,
    topmost_error_stats  ascii,
    costliest_request_stats  ascii,
    ts timestamp,
    duration bigint,
    uhash ascii,
    PRIMARY KEY((to_customer_id, to_deployment_id), ts, uhash)
);
CREATE INDEX to_cluster_id_cs_idx on connection_stats(to_cluster_id);
CREATE INDEX to_host_ip_cs_idx on connection_stats(to_host_ip);
CREATE INDEX to_host_name_cs_idx on connection_stats(to_host_name);
CREATE INDEX svc_info_cluster_id_cs_idx on connection_stats(svc_info_cluster_id);
CREATE INDEX svc_info_name_cs_idx on connection_stats(svc_info_name);
CREATE INDEX svc_info_port_cs_idx on connection_stats(svc_info_port);
CREATE INDEX svc_info_proto_cs_idx on connection_stats(svc_info_proto);
