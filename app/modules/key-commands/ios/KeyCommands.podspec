Pod::Spec.new do |s|
  s.name           = 'KeyCommands'
  s.version        = '1.0.0'
  s.summary        = 'Option+P key command for the BookTimer app'
  s.description    = 'Attaches one UIKeyCommand to the key window root view controller and emits onMeasureShortcut.'
  s.author         = 'Goospel'
  s.homepage       = 'https://booktimer.app'
  s.platforms      = { :ios => '16.4' }
  s.swift_version  = '5.9'
  s.source         = { git: '' }
  s.static_framework = true
  s.dependency 'ExpoModulesCore'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
  s.source_files   = '**/*.swift'
end
