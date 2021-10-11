package sanwu;

import com.google.zxing.WriterException;
import javassist.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.util.Scanner;

class CsRewrite {

    /**
     *
     * @param agentArgs TOTPSecretKey
     * @return
     * @throws Exception
     */
    public static void premain(String agentArgs, Instrumentation inst) throws Exception{
        //Have class resources and TOTP secret
        ClassPool classPool = ClassPool.getDefault();
        Loader loader = new Loader(classPool);


        //插入类路径，进行获取所需修改的类路径
        String className = "server.ManageUser";

        //Find ClassFile to byte[] and give it to classfileBuffer
        CtClass cl = classPool.getCtClass(className);
        byte[] classfileBuffer = cl.toBytecode();

        //defrost server.ManageUser class
        cl.stopPruning(true);
        cl.defrost();
        byte[] classfileBuffer2 = addCsTransformer(className,classfileBuffer,agentArgs);

        cl = classPool.makeClass(new ByteArrayInputStream(classfileBuffer2));
        cl.toClass();
    }

    public static byte[] addCsTransformer(String className,byte[] classfileBuffer,String totpSecretKey) throws Exception{
        ClassPool classPool = ClassPool.getDefault();
        try {
            if (className == null) {
                return classfileBuffer;
            } else if (className.equals("server.ManageUser")) { // 只修改 ManageUser 类
                CtClass cls = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));
                CtMethod ctmethod = cls.getDeclaredMethod("process",
                        new CtClass[]{classPool.get("common.Request")});
                String func = "{"
                        + "if (!$0.authenticated && \"aggressor.authenticate\".equals($1.getCall()) && $1.size() == 3) {"
                        + "   java.lang.String mnickname = $1.arg(0)+\"\";"
                        + "   java.lang.String mpassword = $1.arg(1)+\"\";"
                        + "   java.lang.String mver = $1.arg(2)+\"\";"
                        + "   if(mnickname.length() < 6){ $0.client.writeObject($1.reply(\"Dynamic Code Error.\"));return; };" // 用户名如果低于 6 位就直接 return

                        + "   java.lang.String lastcode = sanwu.GoogleAuthenticationTool.getTOTPCode(\""+totpSecretKey+"\");"// 生成 TOTP 6位数字
                        + "   if(!mnickname.substring(mnickname.length()-6, mnickname.length()).equals(lastcode)) {" // 比对动态口令，如果口令没对上，就 return
                        + "       $0.client.writeObject($1.reply(\"Dynamic Code Error.\"));return;"
                        + "   }"
                        + "}"
                        + "}";
                ctmethod.insertBefore(func); // 把上面的代码插入到 process 函数最前面，如果口令正确，就继续走 cs 常规的流程
                byte[] result = cls.toBytecode();
                //if not detach ,will frost class
                cls.detach();
                return result;

            }
        } catch (Exception ex) {
            ex.printStackTrace();
            System.out.printf("[CSTOTPAgent] PreMain transform Error: %s\n", ex);
        }
        return new byte[]{};
    }

    public static void Generator(){
        //默认用户名为Steve，title为teamwork;
        String title = "teamwork";
        String name = "Steve";

        System.out.println("生成 CobaltStrike TOTP 密钥");
        String secret = GoogleAuthenticationTool.generateSecretKey();
        System.out.println("SecretKey: "+secret);

        //Get User input
        Scanner scanner = new Scanner(System.in);
        System.out.println("Please input your name and title(,): ");
        String line = scanner.nextLine();
        String[] split = line.split(",");
        if (split[0]==" "){
            name = split[0];
            title = split[1];
        }
        String QRString = GoogleAuthenticationTool.spawnScanQRString(name,secret,title);
        String codestring = null;
        try {
            codestring = GoogleAuthenticationTool.createQRCode(QRString,"",400,400);
        } catch (WriterException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(codestring);
        System.out.println("\nPlease in CS args(file teamserver) plus -javaagent:$yourJarFilePath="+secret);
        String totpSecretKey = GoogleAuthenticationTool.getTOTPCode(secret);
    }

    public static void main(String[] args) throws Exception {
            CsRewrite.Generator();
    }
}


