package client

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"strings"
	"time"

	"opscog/models"
)

type HTTPConfig struct {
	Addr               string
	Username           string
	Password           string
	UserAgent          string
	Timeout            time.Duration
	InsecureSkipVerify bool
}

type UDPConfig struct {
	Addr        string
	PayloadSize int
}

type BatchPointsConfig struct {
	Precision        string
	Database         string
	RetentionPolicy  string
	WriteConsistency string
}

type Client interface {
	Write(bp BatchPoints) error
	Query(q Query) (*Response, error)
	Close() error
}

type Query struct {
	Command   string
	Database  string
	Epoch     string
	Precision string
}

type Response struct {
	Results []Result
}

func (r *Response) Error() error {
	for _, result := range r.Results {
		if len(result.Series) == 0 {
			continue
		}
	}
	return nil
}

type Result struct {
	Series []Series
}

type Series struct {
	Name    string
	Columns []string
	Values  [][]interface{}
}

type client struct {
	url        *url.URL
	username   string
	password   string
	useragent  string
	httpClient *http.Client
}

type udpclient struct {
	conn        *net.UDPConn
	payloadSize int
}

func NewQuery(command, database, precision string) Query {
	return Query{
		Command:   command,
		Database:  database,
		Precision: precision,
	}
}

func NewHTTPClient(conf HTTPConfig) (Client, error) {
	if conf.UserAgent == "" {
		conf.UserAgent = "InfluxDBClient"
	}

	u, err := url.Parse(conf.Addr)
	if err != nil {
		return nil, err
	} else if u.Scheme != "http" && u.Scheme != "https" {
		return nil, fmt.Errorf("unsupported protocol scheme: %s, your address must start with http:// or https://", u.Scheme)
	}

	tr := &http.Transport{
		TLSClientConfig: &tls.Config{
			InsecureSkipVerify: conf.InsecureSkipVerify,
		},
	}
	return &client{
		url:       u,
		username:  conf.Username,
		password:  conf.Password,
		useragent: conf.UserAgent,
		httpClient: &http.Client{
			Timeout:   conf.Timeout,
			Transport: tr,
		},
	}, nil
}

func (c *client) Close() error {
	return nil
}

func NewUDPClient(conf UDPConfig) (Client, error) {
	udpAddr, err := net.ResolveUDPAddr("udp", conf.Addr)
	if err != nil {
		return nil, err
	}

	conn, err := net.DialUDP("udp", nil, udpAddr)
	if err != nil {
		return nil, err
	}

	payloadSize := conf.PayloadSize
	if payloadSize == 0 {
		payloadSize = 65535
	}

	return &udpclient{
		conn:        conn,
		payloadSize: payloadSize,
	}, nil
}

func (uc *udpclient) Close() error {
	return uc.conn.Close()
}

func NewBatchPoints(conf BatchPointsConfig) (BatchPoints, error) {
	if conf.Precision == "" {
		conf.Precision = "ns"
	}
	if _, err := time.ParseDuration("1" + conf.Precision); err != nil {
		return nil, err
	}
	return &batchpoints{
		database:         conf.Database,
		precision:        conf.Precision,
		retentionPolicy:  conf.RetentionPolicy,
		writeConsistency: conf.WriteConsistency,
	}, nil
}

type batchpoints struct {
	points           []*Point
	database         string
	precision        string
	retentionPolicy  string
	writeConsistency string
}

func (bp *batchpoints) AddPoint(p *Point) {
	bp.points = append(bp.points, p)
}

func (bp *batchpoints) Points() []*Point {
	return bp.points
}

func (bp *batchpoints) Precision() string {
	return bp.precision
}

func (bp *batchpoints) Database() string {
	return bp.database
}

func (bp *batchpoints) WriteConsistency() string {
	return bp.writeConsistency
}

func (bp *batchpoints) RetentionPolicy() string {
	return bp.retentionPolicy
}

func (bp *batchpoints) SetPrecision(p string) error {
	if _, err := time.ParseDuration("1" + p); err != nil {
		return err
	}
	bp.precision = p
	return nil
}

func (bp *batchpoints) SetDatabase(db string) {
	bp.database = db
}

func (bp *batchpoints) SetWriteConsistency(wc string) {
	bp.writeConsistency = wc
}

func (bp *batchpoints) SetRetentionPolicy(rp string) {
	bp.retentionPolicy = rp
}

type BatchPoints interface {
	AddPoint(p *Point)
	Points() []*Point
	Precision() string
	SetPrecision(s string) error
	Database() string
	SetDatabase(s string)
	WriteConsistency() string
	SetWriteConsistency(s string)
	RetentionPolicy() string
	SetRetentionPolicy(s string)
}

func (c *client) Write(bp BatchPoints) error {
	var precision string
	if bp.Precision() != "" {
		precision = fmt.Sprintf("&precision=%s", bp.Precision())
	}

	u := fmt.Sprintf("%s/write?db=%s%s", c.url.String(), bp.Database(), precision)
	if bp.RetentionPolicy() != "" {
		u = fmt.Sprintf("%s&rp=%s", u, bp.RetentionPolicy())
	}

	var body []byte
	for _, p := range bp.Points() {
		b := p.String()
		body = append(body, []byte(b)...)
		body = append(body, '\n')
	}

	req, err := http.NewRequest("POST", u, bytes.NewReader(body))
	if err != nil {
		return err
	}

	req.SetBasicAuth(c.username, c.password)
	req.Header.Set("Content-Type", "text/plain")
	req.Header.Set("User-Agent", c.useragent)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("status: %s, body: %s", resp.Status, string(body))
	}

	return nil
}

func (c *client) Query(q Query) (*Response, error) {
	u := fmt.Sprintf("%s/query", c.url.String())
	u += fmt.Sprintf("?q=%s", url.QueryEscape(q.Command))

	if q.Database != "" {
		u += fmt.Sprintf("&db=%s", url.QueryEscape(q.Database))
	}
	if q.Epoch != "" {
		u += fmt.Sprintf("&epoch=%s", url.QueryEscape(q.Epoch))
	}
	if q.Precision != "" {
		u += fmt.Sprintf("&precision=%s", url.QueryEscape(q.Precision))
	}

	req, err := http.NewRequest("GET", u, nil)
	if err != nil {
		return nil, err
	}

	req.SetBasicAuth(c.username, c.password)
	req.Header.Set("User-Agent", c.useragent)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("status: %s", resp.Status)
	}

	var response Response
	if err := json.NewDecoder(resp.Body).Decode(&response); err != nil {
		return nil, err
	}

	return &response, nil
}

func (uc *udpclient) Write(bp BatchPoints) error {
	var body []byte
	for _, p := range bp.Points() {
		b := p.PrecisionString(bp.Precision())
		body = append(body, []byte(b)...)
		body = append(body, '\n')
	}

	if len(body) > uc.payloadSize {
		return errors.New("payload too large")
	}

	_, err := uc.conn.Write(body)
	return err
}

func (uc *udpclient) Query(q Query) (*Response, error) {
	return nil, errors.New("Query is not supported with UDP client")
}

type Point struct {
	pt models.Point
}

func NewPoint(
	name string,
	tags map[string]string,
	fields map[string]interface{},
	t ...time.Time,
) (*Point, error) {
	var T time.Time
	if len(t) > 0 {
		T = t[0]
	}

	pt, err := models.NewPoint(name, tags, fields, T)
	if err != nil {
		return nil, err
	}
	return &Point{
		pt: pt,
	}, nil
}

func (p *Point) String() string {
	return p.pt.String()
}

func (p *Point) PrecisionString(precison string) string {
	return p.pt.PrecisionString(precison)
}

func (p *Point) Name() string {
	return p.pt.Name()
}

func (p *Point) Tags() map[string]string {
	return p.pt.Tags()
}

func (p *Point) Time() time.Time {
	return p.pt.Time()
}

func (p *Point) UnixNano() int64 {
	return p.pt.UnixNano()
}

func (p *Point) Fields() map[string]interface{} {
	return p.pt.Fields()
}

func parseKey(key string) (tag bool, keyParts []string) {
	parts := strings.Split(key, ",")
	for _, part := range parts {
		part = strings.TrimSpace(part)
		if len(part) == 0 {
			continue
		}
		if part[0] == '\\' && len(part) > 1 {
			part = part[1:]
		}
		keyParts = append(keyParts, part)
	}
	return
}
