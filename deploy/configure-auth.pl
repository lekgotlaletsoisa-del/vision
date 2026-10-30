#!/usr/bin/env perl
# One-time provisioning for this host's existing Authelia layout. Run with sudo.
# Only add the Vision hostname to the normal application rule and cookie list;
# retain all other text, credentials, users and domain-specific policies verbatim.
use strict;
use warnings;
use File::Copy qw(copy);
use POSIX qw(strftime);

my $path = '/root/authelia/config/configuration.yml';
open my $input, '<', $path or die "Cannot read Authelia configuration: $!\n";
local $/;
my $original = <$input>;
close $input;
my $updated = $original;
my $domain = 'test.letsoisa.co.za';
my ($access) = $original =~ /^(access_control:.*?)(?=^[a-zA-Z_]|\z)/ms;
my ($session) = $original =~ /^(session:.*?)(?=^[a-zA-Z_]|\z)/ms;
defined $access && defined $session or die "Expected Authelia sections were not found.\n";

if (index($access, $domain) < 0) {
    $access =~ s/^(    - dns\.letsoisa\.co\.za\n)/$1    - test.letsoisa.co.za\n/m
        or die "Expected normal-app access list was not found; configuration left unchanged.\n";
    $updated =~ s/^access_control:.*?(?=^[a-zA-Z_]|\z)/$access/ms;
}
if (index($session, $domain) < 0) {
    my $cookie = "  - domain: test.letsoisa.co.za\n"
               . "    authelia_url: https://test.letsoisa.co.za/login\n"
               . "    default_redirection_url: https://test.letsoisa.co.za/\n";
    $session =~ s/^(  cookies:\n)/$1$cookie/m
        or die "Expected domain-cookie list was not found; configuration left unchanged.\n";
    $updated =~ s/^session:.*?(?=^[a-zA-Z_]|\z)/$session/ms;
}
if ($updated eq $original) {
    print "Authelia already includes the Vision hostname; no changes made.\n";
    exit 0;
}
my $backup = $path . '.before-vision-' . strftime('%Y%m%dT%H%M%SZ', gmtime);
copy($path, $backup) or die "Cannot back up Authelia configuration: $!\n";
my $mode = (stat($path))[2] & 0777;
chmod $mode, $backup or die "Cannot preserve backup permissions: $!\n";
my $temporary = $path . '.vision-next';
open my $output, '>', $temporary or die "Cannot stage Authelia configuration: $!\n";
chmod $mode, $temporary or die "Cannot preserve staged permissions: $!\n";
print {$output} $updated;
close $output or die "Cannot finish staging Authelia configuration: $!\n";
rename $temporary, $path or die "Cannot install Authelia configuration: $!\n";
print "Added Vision access rule and hostname cookie. Backup: $backup\n";
print "Validate Authelia before restarting the container.\n";
