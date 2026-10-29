#define _GNU_SOURCE
#include <sys/ptrace.h>
#include <sys/wait.h>
#include <sys/uio.h>
#include <unistd.h>
#include <signal.h>
#include <stdint.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>
struct hwdebug { uint32_t info,pad; struct {uint64_t addr; uint32_t ctrl,pad;} reg[16]; };
__attribute__((noinline)) static void target(void) { __asm__ volatile("nop"); }
int main(void) {
 pid_t child=fork(); int status=0,ok=0; struct hwdebug hw={0};
 if(child<0)return 1;
 if(!child){ if(ptrace(PTRACE_TRACEME,0,0,0)<0)_exit(10);raise(SIGSTOP);target();_exit(0); }
 if(waitpid(child,&status,0)<0||!WIFSTOPPED(status))goto done;
 struct iovec io={&hw,sizeof(hw)};
 if(ptrace(PTRACE_GETREGSET,child,(void*)0x402,&io)<0){perror("GET_HW_BREAK");goto done;}
 printf("hardware_break_slots=%u regset_bytes=%zu\n",hw.info&255,io.iov_len);
 hw.reg[0].addr=(uintptr_t)target;hw.reg[0].ctrl=1|(2<<1)|(15<<5);
 io.iov_len=8+sizeof(hw.reg[0]);
 if(ptrace(PTRACE_SETREGSET,child,(void*)0x402,&io)<0){perror("SET_HW_BREAK");goto done;}
 if(ptrace(PTRACE_CONT,child,0,0)<0)goto done;
 if(waitpid(child,&status,0)<0)goto done;
 if(WIFSTOPPED(status)&&WSTOPSIG(status)==SIGTRAP){
  uint64_t regs[34]; struct iovec ri={regs,sizeof(regs)};
  if(ptrace(PTRACE_GETREGSET,child,(void*)1,&ri)<0)goto done;
  ok=regs[32]==(uintptr_t)target;printf("breakpoint_hit=%d pc_matches=%d\n",WSTOPSIG(status)==SIGTRAP,ok);
  hw.reg[0].ctrl=0;io.iov_len=8+sizeof(hw.reg[0]);
  if(ptrace(PTRACE_SETREGSET,child,(void*)0x402,&io)<0){ok=0;goto done;}
  if(ptrace(PTRACE_DETACH,child,0,0)<0){ok=0;goto done;}
  waitpid(child,&status,0);ok=ok&&WIFEXITED(status)&&WEXITSTATUS(status)==0;
  printf("child_completed_after_detach=%d\n",ok);return ok?0:1;
 }
 done: fprintf(stderr,"selftest_failed status=%x errno=%s\n",status,strerror(errno));kill(child,SIGKILL);waitpid(child,&status,0);return 1;
}
