drop table t0 if exists;
create table t0(c varchar(20), i integer, d date);
insert into t0 values ('first', 1, '2000-01-01');
insert into t0 values ('second', 2, '2000-01-02');
insert into t0 values ('third', 3, '2000-02-01');
select year(d), month(d), day(d) from t0;
/*r
 2000,1,1
 2000,1,2
 2000,2,1
*/select year(d) y, month(d) m, day(d) d from t0 group by year(d), month(d), day(d) order by 1, 2, 3;
--
drop table test if exists;
create table test (sel int, name1 varchar(3), name2 varchar(3));
insert into test (sel, name1, name2) values (0, 'foo', 'bar')
insert into test (sel, name1, name2) values (1, 'baz', 'foo')
insert into test (sel, name1, name2) values (1, 'foo', 'qux')
-- select expressions are all composed of columns of group by
/*r
 baz,foo,1
 foo,bar,1
 foo,qux,1
*/select a.name1, a.name2,
  count(a.name1) as counter from test a group by sel, name1, name2 order by 1, 2, 3
/*r
 foo,1
 foo,1
 qux,1
*/select case when a.sel=1 then a.name2 else a.name1 end as name,
  count(a.name1) as counter from test a group by sel, name1, name2
  order by 1, 2
--
/*r
 foo,2
 qux,1
*/select case when a.sel=1 then a.name2 else a.name1 end as name,
  count(a.name1) as counter from test a group by case when a.sel=1
  then a.name2 else a.name1 end order by 1, 2
--
/*r
 qux,1
 foo,2
*/select case when a.sel=1 then a.name2 else a.name1 end as name,
  count(a.name1) as counter from test a group by case when a.sel=1
  then a.name2 else a.name1 end order by counter, name
--
select coalesce(a.name1, a.name2) as name,count(a.sel) as counter from test a
 group by coalesce(a.name1, a.name2)

select * from (select case when a.sel=1 then a.name2 else a.name1 end as name,
  count(a.name1) as counter from test a group by sel, name1, name2)


drop table test;
drop table trades_a if exists;
create table trades_a(opened timestamp, userid int, points int);
CREATE VIEW trades_scores AS SELECT (EXTRACT(YEAR FROM
 opened) || '-' || EXTRACT(MONTH FROM opened) || '-' ||
 EXTRACT(DAY FROM opened)) AS date, userid, SUM(points)
 AS score FROM trades_a GROUP BY date, userid

insert into trades_a values (now, 1, 10);
select * from trades_scores;
drop table trades_a cascade;

--
drop table PROCESSDETAIL if exists;

create table PROCESSDETAIL (
 ID bigint not null,
 ALERTLEVEL integer not null,
 VALUE varchar(255) not null,
 TSTAMP timestamp not null,
 ATTRIBUTEKEY integer not null,
 PROCESSID bigint not null,
 primary key (ID)
 );


-- testdata (not important to show this problem)
insert into PROCESSDETAIL (ALERTLEVEL, VALUE, TSTAMP, ATTRIBUTEKEY, PROCESSID, ID)
 values (1, 66, '2007-01-01 08:00:00', 28, 9, 1);

insert into PROCESSDETAIL (ALERTLEVEL, VALUE, TSTAMP, ATTRIBUTEKEY, PROCESSID, ID)
 values (0, 67, '2007-01-01 07:59:40', 28, 9, 2);


select * from PROCESSDETAIL t1 where
 exists (
 select
 t2.PROCESSID,
 t2.ATTRIBUTEKEY
 from
 PROCESSDETAIL t2
 where
 t2.PROCESSID=t1.PROCESSID
 and t2.ATTRIBUTEKEY=t1.ATTRIBUTEKEY
 and t2.PROCESSID=9
 and t2.TSTAMP<='2007-01-01 08:00:00'
 group by
 t2.PROCESSID ,
 t2.ATTRIBUTEKEY
 having
 max(t2.TSTAMP)=t1.TSTAMP);

select case when 1<0 then 1 else sum(10) end as res from processdetail;
SHUTDOWN;

