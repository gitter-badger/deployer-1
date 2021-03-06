package com.github.t1.deployer.app.html;

import static java.util.Arrays.*;
import static org.junit.Assert.*;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.github.t1.deployer.model.DataSourceConfig;

@RunWith(MockitoJUnitRunner.class)
public class DataSourcesListHtmlWriterTest extends AbstractHtmlWriterTest<List<DataSourceConfig>> {
    public DataSourcesListHtmlWriterTest() {
        super(new DataSourcesListHtmlWriter());
    }

    @Test
    public void shouldWriteDataSourceList() throws Exception {
        List<DataSourceConfig> dataSources = asList( //
                DataSourceConfig.builder().name("foo").driver("h2").jndiName("jndi:/foo").build(), //
                DataSourceConfig.builder().name("bar").driver("postgresql").jndiName("jndi:/bar").build());

        String entity = write(dataSources);

        assertEquals(readFile(), entity);
    }
}
