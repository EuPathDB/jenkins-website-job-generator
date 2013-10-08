public class JobConfigurator {

  def jenkins = hudson.model.Hudson.instance
  def jobFactory
  java.io.PrintStream console
  def masterMap
  
  public JobConfigurator(jobFactory) {
    this.jobFactory = jobFactory
    /**
      Jenkins' Groovy plugin requirement: Get handle on out
      so println in Classes will send output to the script console,
      http://stackoverflow.com/questions/7742472/groovy-script-in-jenkins-println-output-disappears-when-called-inside-class-envi
      http://mriet.wordpress.com/2011/06/23/groovy-jenkins-system-script/
    **/
    this.console = jobFactory.getBinding().getVariable('out')
    this.masterMap = makeMasterMap()
  }

  public void createJobs() {
    console.println()
    masterMap.each {
      def jobName = it.key
      createJob(jobName)
    }
    console.println()
  }
  

  public Map makeMasterMap() {
    console.println()
    def map = [:]
    Sites.inclusiveHosts.each {
      def host = it
        Sites.inclusiveProducts.each {
          def product = it
          def jobName = "${host}.${product.toLowerCase()}.org"
          def webapp = Values.productSpecificConfig[product]['webapp']
          def existingJob = jenkins.getJob(jobName)
          def hostconf = Values.hostSpecificConfig[host]
          map[jobName] = [
            label : hostconf['label'],
            description : Values.stdDescription(jobName, "boo"),
            logRotator : hostconf['logRotator'] ?: [7, -1, -1, -1],
            quietPeriod : hostconf['quietPeriod'] ?: null,
            customWorkspace : '/var/www/' + jobName + '/project_home',
            scm : getSvnLocations(moduleLocations(jobName, existingJob, Values.svnDefaultLocations)),
            scmSchedule : hostconf['scmSchedule'] ?: null,
            timeout : hostconf['timeout'] ?: null,
            rebuilderStep : hostconf['rebuilderStep'](host, product, webapp),
            testngStep : hostconf['testngStep'] ?
                              hostconf['testngStep'](host, product, webapp) : 
                              null,
            jabberNotification : hostconf['jabberNotification'] ? 
                  hostconf['jabberNotification'](hostconf['jabberContacts']) : null,
            extendedEmail : hostconf['extendedEmail'] ?: null,
          ]
        }
    }
    addCustomMaps(map)
    return map
  }


  public void addCustomMaps(map) {
    console.println()
    Sites.customJobs.each { jobName, conf ->
          if (conf == null) {
            map.remove(jobName)
            console.println 'Custom ' + jobName + ' is null, no conf generated.'
            return
          }
          def product = conf['product']
          def webapp = conf['webapp']
          def host = conf['host']
          def existingJob = jenkins.getJob(jobName)
          map[jobName] = [
            label : conf['label'],
            description : Values.stdDescription(jobName, "boo"),
            logRotator : conf['logRotator'] ?: null,
            quietPeriod : conf['quietPeriod'] ?: null,
            customWorkspace : '/var/www/' + jobName + '/project_home',
            scm : getSvnLocations(moduleLocations(jobName, existingJob, Values.svnDefaultLocations)),
            scmSchedule : conf['scmSchedule'] ?: null,
            timeout : conf['timeout'] ?: null,
            rebuilderStep : conf['rebuilderStep'](host, product, webapp),
            testngStep : conf['testngStep'] ? conf['testngStep'](host, product, webapp) : null,
            jabberNotification : conf['jabberNotification'] ? conf['jabberNotification'](conf['jabberContacts']) : null,
            extendedEmail : conf['extendedEmail'] ?: null,
         ]

    }
  }

  public void createJob(jobName) {
    if (jobName.toLowerCase().contains('orthomcl'))
      throw new java.lang.RuntimeException(jobName + " looks like an OrthoMCL site. It requires additional configurations not supported here. Remove it.")
    console.println "Creating " + jobName
    jobFactory.job {
      name jobName
      label 'foo'
      description  masterMap[jobName]['description']

    } // job
  } //createJob


  /**
      <hudson.plugins.testng.Publisher plugin="testng-plugin@1.5">
        <reportFilenamePattern>test_home/results/**</reportFilenamePattern>
        <escapeTestDescp>true</escapeTestDescp>
        <escapeExceptionMsg>true</escapeExceptionMsg>
      </hudson.plugins.testng.Publisher>
  **/
  def testngPubliser() {
    {project -> project/publishers/'hudson.plugins.testng.Publisher' {
      reportFilenamePattern 'test_home/results/**'
      escapeTestDescp 'true'
      escapeExceptionMsg 'true'
    }
    }
  }

  // convert SubversionSCM.ModuleLocation fields to a map
  def moduleLocations(jobName, job, svnDefaultLocations) {
    if (job == null) {
      console.println jobName + " is new, using default svn locations"
      return svnDefaultLocations
    }

    if ( ! job.scm.hasProperty('locations')) {
      console.println jobName + " exists, but no scm defined; using default svn locations"
      return svnDefaultLocations
    }

    def locations = [:]
    job.scm.locations.each{
      if ( ! it.local && ! it.remote) return
      locations.put(it.local, it.remote)
    }
    
    if (locations.size() == 0) {
      console.println jobName + " exists, but no valid svn locations; using default svn locations"
      return svnDefaultLocations
    }

    console.println "Existing job, using existing svn locations"
    return locations
  }

  def getSvnLocations(svnLocations) {
    {it ->
        def installUrl = svnLocations['install']
        if (installUrl == null) 
          throw new java.lang.NullPointerException("SCM location for 'install' is not defined'")
        def firstKey = svnLocations.find().key
        svn(svnLocations[firstKey], firstKey) {
          svnLocations.each { localValue, remoteValue -> 
            if (localValue == firstKey) { return }
            it / locations << 'hudson.scm.SubversionSCM_-ModuleLocation' {
              remote remoteValue
              local localValue
            }
            
            it / browser(class:"hudson.plugins.websvn2.WebSVN2RepositoryBrowser") {
              url "http://websvn.apidb.org/revision.php?repname=TBD"
              baseUrl "http://websvn.apidb.org"
              repname "repname=TBD&amp;"
            }
            
        }
      }
    }
  }

}

