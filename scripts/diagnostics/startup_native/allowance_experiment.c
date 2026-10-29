#define _GNU_SOURCE
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sys/uio.h>
#include <sys/types.h>
#include <dirent.h>
#include <unistd.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#define MAXT 512
struct hwdebug {uint32_t info,pad;struct {uint64_t addr;uint32_t ctrl,pad;} reg[16];};
struct thread {pid_t tid;int stopped,stepping;};
static struct thread ts[MAXT];static int nt,events,hits;static volatile sig_atomic_t quit;
static int allowance_added;
static uintptr_t base;static pid_t pid;static const uintptr_t points[]={0x3277f4,0x1666ec,0x29c1c0,0x2f46d0,0x29dc00};
static const char *names[]={"redo","rebuildLonglink","longResponse","shortResponse","longFinal"};
static long ms(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec*1000+t.tv_nsec/1000000;}
static void stop(int x){(void)x;quit=1;}
static int readmem(uintptr_t a,void *out,size_t n){struct iovec l={out,n},r={(void*)a,n};return process_vm_readv(pid,&l,1,&r,1,0)==(ssize_t)n;}
static int breaks(pid_t tid,int enable){struct hwdebug h={0};struct iovec io={&h,sizeof(h)};if(ptrace(PTRACE_GETREGSET,tid,(void*)0x402,&io)<0)return -1;if((h.info&255)<5){errno=ENOSPC;return -1;}for(int i=0;i<5;i++){h.reg[i].addr=base+points[i];h.reg[i].ctrl=enable?(1|(2<<1)|(15<<5)):0;}io.iov_len=8+5*sizeof(h.reg[0]);return ptrace(PTRACE_SETREGSET,tid,(void*)0x402,&io);}
static struct thread *get(pid_t t){for(int i=0;i<nt;i++)if(ts[i].tid==t)return &ts[i];return NULL;}
static void enroll(void){char path[80];snprintf(path,sizeof(path),"/proc/%d/task",pid);DIR*d=opendir(path);if(!d)return;struct dirent *e;while((e=readdir(d))){pid_t t=atoi(e->d_name);if(t<=0||get(t)||nt>=MAXT)continue;/* Seize does not stop or signal the application. */if(ptrace(PTRACE_SEIZE,t,0,0)<0)continue;struct thread *s=&ts[nt++];s->tid=t;if(ptrace(PTRACE_INTERRUPT,t,0,0)<0){ptrace(PTRACE_DETACH,t,0,0);s->tid=0;}}closedir(d);}
static void record(uint64_t*r,int k,pid_t tid){int type=0,code=0;uint32_t taskid=0,cmd=0;int32_t retry=0;uintptr_t running=0;int readable=0;
 if(k==2||k==3){type=(int)r[2];code=(int)r[3];if(type!=9||code!=-7)return;readable=readmem(r[1]+16,&taskid,4)&&readmem(r[1]+20,&cmd,4)&&readmem(r[1]+2400,&retry,4)&&readmem(r[1]+2352,&running,8);}
 if(k==4){type=(int)r[21];code=(int)r[26];readable=readmem(r[19]+16,&taskid,4)&&readmem(r[19]+20,&cmd,4)&&readmem(r[19]+2400,&retry,4);}
 if(k==2 && readable && cmd==121 && retry==1 && !allowance_added){
 int32_t extra=2;struct iovec local={&extra,4},remote={(void*)(r[1]+2400),4};
 if(process_vm_writev(pid,&local,1,&remote,1,0)==4){allowance_added=1;printf("experiment allowance_added task=%u retry=1->2 ms=%ld\n",taskid,ms());retry=2;}
 else {printf("experiment write_failed errno=%d\n",errno);quit=1;}
 }
 printf("event ms=%ld tid=%d site=%s pc=0x%lx lr=0x%lx type=%d code=%d task=%u cmd=%u retry=%d running=%d readable=%d fp=",ms(),tid,names[k],(unsigned long)(r[32]-base),(unsigned long)(r[30]-base),type,code,taskid,cmd,retry,running!=0,readable);
 uintptr_t fp=r[29],sp=r[31];for(int i=0;i<10;i++){uintptr_t pair[2];if(fp<sp||fp-sp>1024*1024||fp%16||!readmem(fp,pair,sizeof(pair)))break;uintptr_t lr=pair[1];if(lr>=base&&lr<base+0x400000)printf("0x%lx,",(unsigned long)(lr-base));if(pair[0]<=fp)break;fp=pair[0];}puts("");fflush(stdout);events++;
}
static uintptr_t loadbase(void){char path[80],line[1024];snprintf(path,sizeof(path),"/proc/%d/maps",pid);FILE*f=fopen(path,"r");if(!f)return 0;uintptr_t result=0;while(fgets(line,sizeof(line),f)){unsigned long a,b,off;char perms[8];if(strstr(line,"/libwechatnetwork.so")&&sscanf(line,"%lx-%lx %7s %lx",&a,&b,perms,&off)==4){result=a-off;break;}}fclose(f);return result;}
static void cleanup(void){for(int i=0;i<nt;i++)if(ts[i].tid&&!ts[i].stopped)ptrace(PTRACE_INTERRUPT,ts[i].tid,0,0);long end=ms()+2000;int detached=0;while(ms()<end){int active=0;for(int i=0;i<nt;i++){struct thread*s=&ts[i];if(!s->tid)continue;active++;int status;if(!s->stopped){pid_t w=waitpid(s->tid,&status,__WALL|WNOHANG);if(w==s->tid){if(WIFEXITED(status)||WIFSIGNALED(status)){s->tid=0;continue;}if(WIFSTOPPED(status))s->stopped=1;}}if(s->stopped){breaks(s->tid,0);if(ptrace(PTRACE_DETACH,s->tid,0,0)==0||errno==ESRCH){s->tid=0;detached++;}}}if(!active)break;usleep(1000);}int remaining=0;for(int i=0;i<nt;i++)remaining+=ts[i].tid!=0;printf("cleanup detached=%d remaining=%d\n",detached,remaining);}
int main(int argc,char**argv){if(argc!=3){fprintf(stderr,"usage: netprobe PID SECONDS\n");return 2;}pid=atoi(argv[1]);int seconds=atoi(argv[2]);if(pid<2||seconds<1||seconds>180)return 2;signal(SIGINT,stop);signal(SIGTERM,stop);setvbuf(stdout,NULL,_IOLBF,0);long end=ms()+seconds*1000;while(!(base=loadbase())&&ms()<end&&!quit)usleep(10000);if(!base){puts("library_not_loaded");return 1;}uint32_t expected[]={0xd10503ff,0xd10543ff,0xa9ba7bfd,0xa9ba7bfd,0x927ff908}; for(int k=0;k<5;k++){uint32_t word=0;if(!readmem(base+points[k],&word,4)){puts("code_read_failed");return 1;}if(word!=expected[k]){printf("code_mismatch site=%s actual=%08x\n",names[k],word);return 1;}} printf("armed pid=%d base=0x%lx seconds=%d\n",pid,(unsigned long)base,seconds);long next=0;while(!quit&&ms()<end&&events<128){if(ms()>=next){enroll();next=ms()+100;}int status;pid_t t=waitpid(-1,&status,__WALL|WNOHANG);if(t<=0){if(kill(pid,0)<0&&errno==ESRCH)break;usleep(1000);continue;}struct thread*s=get(t);if(!s)continue;if(WIFEXITED(status)||WIFSIGNALED(status)){s->tid=0;continue;}if(!WIFSTOPPED(status))continue;s->stopped=1;int sig=WSTOPSIG(status);unsigned event=(unsigned)status>>16;
 if(event==PTRACE_EVENT_STOP){struct hwdebug initial={0};struct iovec hi={&initial,sizeof(initial)};if(ptrace(PTRACE_GETREGSET,t,(void*)0x402,&hi)<0){quit=1;continue;}int occupied=0;for(int j=0;j<5;j++)occupied|=initial.reg[j].ctrl&1;if(occupied){printf("existing_debugger_breakpoint tid=%d; refusing overwrite\n",t);ptrace(PTRACE_DETACH,t,0,0);s->tid=0;quit=1;continue;}if(breaks(t,1)<0){printf("break_install_failed tid=%d errno=%d\n",t,errno);quit=1;continue;}s->stepping=0;}
 else if(sig==SIGTRAP){if(++hits>4096){quit=1;continue;}if(s->stepping){if(breaks(t,1)<0){quit=1;continue;}s->stepping=0;}else{uint64_t r[34]={0};struct iovec io={r,sizeof(r)};int hit=-1;if(ptrace(PTRACE_GETREGSET,t,(void*)1,&io)==0)for(int k=0;k<5;k++)if(r[32]==base+points[k]){hit=k;break;}if(hit<0){/* Preserve unrelated application traps. */ptrace(PTRACE_CONT,t,0,(void*)(long)sig);s->stopped=0;continue;}record(r,hit,t);if(breaks(t,0)<0){quit=1;continue;}s->stepping=1;if(ptrace(PTRACE_SINGLESTEP,t,0,0)<0){quit=1;continue;}s->stopped=0;continue;}}
 else {if(ptrace(PTRACE_CONT,t,0,(void*)(long)sig)<0){quit=1;continue;}s->stopped=0;continue;}
 if(ptrace(PTRACE_CONT,t,0,0)<0){quit=1;continue;}s->stopped=0;
 }cleanup();printf("finished events=%d enrolled=%d\n",events,nt);return 0;}
