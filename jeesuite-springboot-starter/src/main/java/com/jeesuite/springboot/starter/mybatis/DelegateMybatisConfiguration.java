/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2016 abel533@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.jeesuite.springboot.starter.mybatis;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

import com.github.pagehelper.PageInterceptor;
import com.jeesuite.mybatis.datasource.MutiRouteDataSource;
import com.jeesuite.mybatis.plugin.JeesuiteMybatisInterceptor;

import tk.mybatis.spring.mapper.MapperScannerConfigurer;


@Configuration
@EnableConfigurationProperties(MybatisProperties.class)
@EnableTransactionManagement
public class DelegateMybatisConfiguration implements TransactionManagementConfigurer {

	@Autowired
	private MybatisProperties properties;
	
	private MutiRouteDataSource dataSource;
	
	@Bean(name = "dataSource")
    @Order(0)
    public DataSource dataSourceBean(){
    	if(dataSource == null){
    		dataSource = new MutiRouteDataSource();
        	dataSource.afterPropertiesSet();
    	}
    	return dataSource;
    }

    @Bean(name = "sqlSessionFactory")
    @Order(1)
    public SqlSessionFactory sqlSessionFactoryBean(@Qualifier("dataSource") DataSource dataSource) {
        SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
        bean.setDataSource(dataSource);
        bean.setTypeAliasesPackage(properties.getTypeAliasesPackage());

        //添加XML目录
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            bean.setMapperLocations(resolver.getResources(properties.getMapperLocations()));
            List<Interceptor> plugins = new ArrayList<>();
            //分页
            if(properties.isPaginationEnabled()){
            	PageInterceptor interceptor = new PageInterceptor();
            	plugins.add(interceptor);
            }
            //
            String interceptorHandlers = null;
            if(properties.isCacheEnabled()){
            	interceptorHandlers = "cache";
            }
            
            if(properties.isRwRouteEnabled()){
            	interceptorHandlers = interceptorHandlers == null ? "rwRoute" : interceptorHandlers + ",rwRoute";
            }
            
            if(properties.isDbShardEnabled()){
            	interceptorHandlers = interceptorHandlers == null ? "dbShard" : interceptorHandlers + ",dbShard";
            }
            
            if(interceptorHandlers != null){            	
            	JeesuiteMybatisInterceptor interceptor = new JeesuiteMybatisInterceptor();
            	interceptor.setCrudDriver("mapper3");
            	interceptor.setMapperLocations(properties.getMapperLocations());
            	interceptor.setInterceptorHandlers(interceptorHandlers);
            	plugins.add(interceptor);
            }
            
           if(plugins.size() > 0){
        	   bean.setPlugins(plugins.toArray(new Interceptor[0]));
           }
            return bean.getObject();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        
    }

    @Bean
    @Order(2)
    public SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory) {
        return new SqlSessionTemplate(sqlSessionFactory);
    }

    @Bean
    @Override
    @Order(2)
    public PlatformTransactionManager annotationDrivenTransactionManager() {
        return new DataSourceTransactionManager(dataSource);
    }
    
    @Bean
    @Order(3)
    public MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer mapperScannerConfigurer = new MapperScannerConfigurer();
        mapperScannerConfigurer.setSqlSessionFactoryBeanName("sqlSessionFactory");
        mapperScannerConfigurer.setBasePackage(properties.getMapperBasePackage());
        Properties properties = new Properties();
        if(this.properties.getBaseMapperClass() != null){        	
        	properties.setProperty("mappers", this.properties.getBaseMapperClass());
        }
        properties.setProperty("notEmpty", "false");
        properties.setProperty("IDENTITY", "MYSQL");
        mapperScannerConfigurer.setProperties(properties);
        return mapperScannerConfigurer;
    }
}