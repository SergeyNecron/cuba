-- Change SEC_FILTER.XML to Lob

alter table SYS_SCHEDULED_TASK alter METHOD_PARAMS type varchar(4000);
