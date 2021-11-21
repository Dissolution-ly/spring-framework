package wang.lixuanyu.test;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.FileSystemXmlApplicationContext;

public class Entrance {

	public static void main(String[] args) {
		// ApplicationContext (高级IOC)
		ApplicationContext context = new ClassPathXmlApplicationContext("spring.xml");
		ApplicationContext fileContext = new FileSystemXmlApplicationContext("D:\\spring.xml");
		ApplicationContext annotationContext = new AnnotationConfigApplicationContext();
		System.out.println(context);
	}

}
