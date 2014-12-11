# -*- mode: ruby -*-
# vi: set ft=ruby :

VAGRANTFILE_API_VERSION = "2"

Vagrant.configure(VAGRANTFILE_API_VERSION) do |config|
  # Configure VM Ram usage
  config.vm.provider :virtualbox do |vb|
    vb.customize ["modifyvm", :id, "--memory", "1024"]
#    vb.gui = true
  end
  config.vm.box = "ubuntu/trusty64"
  #config.vm.box_url = "https://github.com/akovacs/archlinux-vagrant-boxes.git"
  config.ssh.forward_agent = true
  config.vm.provision "shell", path: "scripts/bootstrap.sh"
  
  # Boot 2 virtual machines
  (1..12).each do |i|
    config.vm.define "cs244b-#{i}" do |s|
      s.vm.provision "file", source: "configuration#{i}.yml", destination: "configuration#{i}.yml"
      s.vm.provision "file", source: "target/cs244b-final-project-0.0.1-SNAPSHOT.jar", destination: "cs244b-final-project.jar"
      #s.vm.hostname = "cs244b-#{i}"
      #s.vm.network "public_network", use_dhcp_assigned_default_route: true
      s.vm.network "private_network", ip: "192.168.50.#{i}", netmask: "255.255.255.0", virtualbox__intnet: "cs244b", drop_nat_interface_default_route: true
      s.vm.network "forwarded_port", host: 8078+2*i, guest: 8078+2*i # webserver
      s.vm.network "forwarded_port", host: 8079+2*i, guest: 8079+2*i # RMI
      s.vm.network "forwarded_port", host: 10000+i, guest: 10000+i #JDWP for debugging
      s.vm.provision "shell", path: "scripts/install.sh", args:"#{i}", privileged: "true"
    end
  end
end

