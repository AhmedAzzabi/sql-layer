
package com.akiban.server.service.routines;

import com.akiban.ais.model.AkibanInformationSchema;
import com.akiban.ais.model.Routine;
import com.akiban.ais.model.TableName;
import com.akiban.ais.model.SQLJJar;
import com.akiban.ais.model.aisb2.AISBBasedBuilder;
import com.akiban.ais.model.aisb2.NewAISBuilder;
import com.akiban.ais.model.aisb2.NewRoutineBuilder;
import com.akiban.qp.loadableplan.LoadablePlan;
import com.akiban.server.error.SQLJInstanceException;
import com.akiban.server.error.NoSuchSQLJJarException;
import com.akiban.server.error.NoSuchRoutineException;
import com.akiban.server.service.Service;
import com.akiban.server.service.config.ConfigurationService;
import com.akiban.server.service.dxl.DXLService;
import com.akiban.server.service.session.Session;
import com.akiban.server.store.SchemaManager;

import com.akiban.qp.loadableplan.std.DumpGroupLoadablePlan;
import com.akiban.qp.loadableplan.std.PersistitCLILoadablePlan;

import com.google.inject.Singleton;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Singleton
public final class RoutineLoaderImpl implements RoutineLoader, Service {

    private final DXLService dxlService;
    private final SchemaManager schemaManager;
    private final Map<TableName,ClassLoader> classLoaders = new HashMap<>();
    private final Map<TableName,LoadablePlan<?>> loadablePlans = new HashMap<>();
    private final Map<TableName,Method> javaMethods = new HashMap<>();
    private final ScriptCache scripts;
    private final static Logger logger = LoggerFactory.getLogger(RoutineLoaderImpl.class);

    @Inject @SuppressWarnings("unused")
    public RoutineLoaderImpl(DXLService dxlService,
                             SchemaManager schemaManager,
                             ConfigurationService configService,
                             ScriptEngineManagerProvider engineProvider) {
        this.dxlService = dxlService;
        this.schemaManager = schemaManager;
        scripts = new ScriptCache(dxlService, engineProvider);
    }

    private AkibanInformationSchema ais(Session session) {
        return dxlService.ddlFunctions().getAIS(session);
    }

    /* RoutineLoader */

    @Override
    public ClassLoader loadSQLJJar(Session session, TableName jarName) {
        if (jarName == null)
            return getClass().getClassLoader();
        synchronized (classLoaders) {
            ClassLoader loader = classLoaders.get(jarName);
            if (loader != null)
                return loader;

            SQLJJar sqljJar = ais(session).getSQLJJar(jarName);
            if (sqljJar == null)
                throw new NoSuchSQLJJarException(jarName);
            loader = new URLClassLoader(new URL[] { sqljJar.getURL() });
            classLoaders.put(jarName, loader);
            return loader;
        }
    }

    @Override
    public void unloadSQLJJar(Session session, TableName jarName) {
        synchronized (classLoaders) {
            classLoaders.remove(jarName);
        }        
    }

    @Override
    public LoadablePlan<?> loadLoadablePlan(Session session, TableName routineName) {
        LoadablePlan<?> loadablePlan;
        synchronized (loadablePlans) {
            loadablePlan = loadablePlans.get(routineName);
            if (loadablePlan == null) {
                Routine routine = ais(session).getRoutine(routineName);
                if (routine == null)
                    throw new NoSuchRoutineException(routineName);
                if (routine.getCallingConvention() != Routine.CallingConvention.LOADABLE_PLAN)
                    throw new SQLJInstanceException(routineName, "Routine was not loadable plan");
                TableName jarName = null;
                if (routine.getSQLJJar() != null)
                    jarName = routine.getSQLJJar().getName();
                ClassLoader classLoader = loadSQLJJar(session, jarName);
                try {
                    loadablePlan = (LoadablePlan<?>)
                        Class.forName(routine.getClassName(), true, classLoader).newInstance();
                }
                catch (Exception ex) {
                    throw new SQLJInstanceException(routineName, ex);
                }
                loadablePlans.put(routineName, loadablePlan);
            }
        }
        synchronized (loadablePlan) {
            if (loadablePlan.ais() != ais(session))
                loadablePlan.ais(ais(session));
        }
        return loadablePlan;
    }

    @Override
    public Method loadJavaMethod(Session session, TableName routineName) {
        synchronized (javaMethods) {
            Method javaMethod = javaMethods.get(routineName);
            if (javaMethod != null)
                return javaMethod;
            Routine routine = ais(session).getRoutine(routineName);
            if (routine == null)
                throw new NoSuchRoutineException(routineName);
            if (routine.getCallingConvention() != Routine.CallingConvention.JAVA)
                throw new SQLJInstanceException(routineName, "Routine was not SQL/J");
            TableName jarName = null;
            if (routine.getSQLJJar() != null)
                jarName = routine.getSQLJJar().getName();
            ClassLoader classLoader = loadSQLJJar(session, jarName);
            Class<?> clazz;
            try {
                clazz = Class.forName(routine.getClassName(), true, classLoader);
            }
            catch (Exception ex) {
                throw new SQLJInstanceException(routineName, ex);
            }
            String methodName = routine.getMethodName();
            String methodArgs = null;
            int idx = methodName.indexOf('(');
            if (idx >= 0) {
                methodArgs = methodName.substring(idx+1);
                methodName = methodName.substring(0, idx);
            }
            for (Method method : clazz.getMethods()) {
                if (((method.getModifiers() & (Modifier.PUBLIC | Modifier.STATIC)) ==
                                              (Modifier.PUBLIC | Modifier.STATIC)) &&
                    method.getName().equals(methodName) &&
                    ((methodArgs == null) ||
                     (method.toString().indexOf(methodArgs) > 0))) {
                    javaMethod = method;
                    break;
                }
            }
            javaMethods.put(routineName, javaMethod);
            return javaMethod;
        }
    }

    @Override
    public boolean isScriptLanguage(Session session, String language) {
        return scripts.isScriptLanguage(session, language);
    }

    @Override
    public ScriptPool<ScriptEvaluator> getScriptEvaluator(Session session, TableName routineName) {
        return scripts.getScriptEvaluator(session, routineName);
    }

    @Override
    public ScriptPool<ScriptInvoker> getScriptInvoker(Session session, TableName routineName) {
        return scripts.getScriptInvoker(session, routineName);
    }

    @Override
    public void unloadRoutine(Session session, TableName routineName) {
        synchronized (loadablePlans) {
            loadablePlans.remove(routineName);
        }
        synchronized (javaMethods) {
            javaMethods.remove(routineName);
        }
        scripts.remove(routineName);
    }

    /* Service */

    @Override
    public void start() {
        registerSystemProcedures();
    }

    @Override
    public void stop() {
        if (false)              // Only started once for server and AIS wiped for tests.
            unregisterSystemProcedures();
    }

    @Override
    public void crash() {
        stop();
    }

    public static final int IDENT_MAX = 128;
    public static final int PATH_MAX = 1024;
    public static final int COMMAND_MAX = 1024;

    private void registerSystemProcedures() {
        NewAISBuilder aisb = AISBBasedBuilder.create();

        aisb.defaultSchema(TableName.SYS_SCHEMA);
        aisb.procedure("dump_group")
            .language("java", Routine.CallingConvention.LOADABLE_PLAN)
            .paramStringIn("schema_name", IDENT_MAX)
            .paramStringIn("table_name", IDENT_MAX)
            .paramLongIn("insert_max_row_count")
            .externalName("com.akiban.qp.loadableplan.std.DumpGroupLoadablePlan");
        aisb.procedure("persistitcli")
            .language("java", Routine.CallingConvention.LOADABLE_PLAN)
            .paramStringIn("command", COMMAND_MAX)
            .externalName("com.akiban.qp.loadableplan.std.PersistitCLILoadablePlan");

        aisb.defaultSchema(TableName.SQLJ_SCHEMA);
        aisb.procedure("install_jar")
            .language("java", Routine.CallingConvention.JAVA)
            .paramStringIn("url", PATH_MAX)
            .paramStringIn("jar", PATH_MAX)
            .paramLongIn("deploy")
            .externalName("com.akiban.server.service.routines.SQLJJarRoutines", "install");
        aisb.procedure("replace_jar")
            .language("java", Routine.CallingConvention.JAVA)
            .paramStringIn("url", PATH_MAX)
            .paramStringIn("jar", PATH_MAX)
            .externalName("com.akiban.server.service.routines.SQLJJarRoutines", "replace");
        aisb.procedure("remove_jar")
            .language("java", Routine.CallingConvention.JAVA)
            .paramStringIn("jar", PATH_MAX)
            .paramLongIn("undeploy")
            .externalName("com.akiban.server.service.routines.SQLJJarRoutines", "remove");

        Collection<Routine> procs = aisb.ais().getRoutines().values();
        for (Routine proc : procs) {
            schemaManager.registerSystemRoutine(proc);
        }
    }

    private void unregisterSystemProcedures() {
        schemaManager.unRegisterSystemRoutine(new TableName(TableName.SYS_SCHEMA,
                                                            "dump_group"));
        schemaManager.unRegisterSystemRoutine(new TableName(TableName.SYS_SCHEMA,
                                                            "persistitcli"));
    }
}
