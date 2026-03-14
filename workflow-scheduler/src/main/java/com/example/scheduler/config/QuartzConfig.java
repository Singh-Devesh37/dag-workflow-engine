package com.example.scheduler.config;

import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.Properties;

@Configuration
public class QuartzConfig {
  @Bean
  public SchedulerFactoryBean schedulerFactoryBean(
      DataSource dataSource, ApplicationContext context) {
    SpringBeanJobFactory jobFactory = new SpringBeanJobFactory();
    jobFactory.setApplicationContext(context);

    Properties props = new Properties();
    props.setProperty("org.quartz.scheduler.instanceName", "WorkflowQuartzScheduler");
    props.setProperty("org.quartz.scheduler.instanceId", "AUTO");
    props.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
    props.setProperty(
        "org.quartz.jobStore.driverDelegateClass",
        "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
    props.setProperty("org.quartz.jobStore.useProperties", "false");
    props.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
    props.setProperty("org.quartz.threadPool.threadCount", "10");

    SchedulerFactoryBean factory = new SchedulerFactoryBean();
    factory.setDataSource(dataSource);
    factory.setJobFactory(jobFactory);
    factory.setQuartzProperties(props);
    factory.setWaitForJobsToCompleteOnShutdown(true);
    return factory;
  }

  @Bean
  public Properties quartzProperties() throws IOException {
    PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
    propertiesFactoryBean.setLocation(new ClassPathResource("/quartz.properties"));
    propertiesFactoryBean.afterPropertiesSet();
    return propertiesFactoryBean.getObject();
  }
}
