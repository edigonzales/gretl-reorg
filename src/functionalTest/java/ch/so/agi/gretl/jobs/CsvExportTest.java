package ch.so.agi.gretl.jobs;

import ch.so.agi.gretl.util.TestUtilSqlPostgres;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.ClassRule;
import org.junit.Test;
import org.testcontainers.containers.PostgisContainerProvider;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.Assert.*;

import java.io.File;
import java.sql.Connection;
import java.sql.Statement;

public class CsvExportTest {
    static String WAIT_PATTERN = ".*database system is ready to accept connections.*\\s";
    
    Connection con = null;
    
    @ClassRule
    public static PostgreSQLContainer postgres = 
        (PostgreSQLContainer) new PostgisContainerProvider()
        .newInstance().withDatabaseName("gretl")
        .withUsername("ddluser")
        .withInitScript("init_postgresql.sql")
        .waitingFor(Wait.forLogMessage(WAIT_PATTERN, 2));

    @Test
    public void exportOk() throws Exception {
        String schemaName = "csvexport".toLowerCase();
        try {
            // prepare postgres
            con = TestUtilSqlPostgres.connect(postgres);
            TestUtilSqlPostgres.createOrReplaceSchema(con, schemaName);
            
            Statement s1 = con.createStatement();
            s1.execute("CREATE TABLE "+schemaName+".exportdata(t_id serial, \"Aint\" integer, adec decimal(7,1), atext varchar(40), aenum varchar(120),adate date, atimestamp timestamp, aboolean boolean, aextra varchar(40))");
            s1.execute("INSERT INTO "+schemaName+".exportdata(t_id, \"Aint\", adec, atext, adate, atimestamp, aboolean) VALUES (1,2,3.4,'abc','2013-10-21','2015-02-16T08:35:45.000','true')");
            s1.execute("INSERT INTO "+schemaName+".exportdata(t_id) VALUES (2)");
            s1.close();
            TestUtilSqlPostgres.grantDataModsInSchemaToUser(con, schemaName, TestUtilSqlPostgres.CON_DMLUSER);

            con.commit();
            con.close();

            // run job
            BuildResult result = GradleRunner.create()
                    .withProjectDir(new File("src/functionalTest/jobs/CsvExport/"))
                    .withArguments("-i")
                    .withArguments("-Pdb_uri=" + postgres.getJdbcUrl())
                    .withPluginClasspath()
                    .build();

            // check results
            assertEquals(SUCCESS, result.task(":csvexport").getOutcome());

            System.out.println("cwd "+new File(".").getAbsolutePath());
            java.io.LineNumberReader reader=new java.io.LineNumberReader(new java.io.InputStreamReader(new java.io.FileInputStream(new File("src/functionalTest/jobs/CsvExport/data.csv"))));
            String line=reader.readLine();
               assertEquals("\"t_id\",\"Aint\",\"adec\",\"atext\",\"aenum\",\"adate\",\"atimestamp\",\"aboolean\"", line);
            line=reader.readLine();
               assertEquals("\"1\",\"2\",\"3.4\",\"abc\",\"\",\"2013-10-21\",\"2015-02-16T08:35:45.000\",\"true\"", line);
            line=reader.readLine();
               assertEquals("\"2\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"", line);
            reader.close();
        } finally {
            con.close();
        }
    }
}
