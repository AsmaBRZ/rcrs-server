import matplotlib.pyplot as plt
import numpy as np

time=np.arange(1,301)
array=np.zeros(300)
a=[]

fichiers=np.arange(5)

for f in fichiers:
    i=0
    with open(str(f)+".txt", "r") as ins:
        for line in ins:
            l=line.split(" ")            
            array[i]=array[i]+int(l[1])
            i=i+1

print (array)


plt.plot(array)
plt.ylabel("Nombre d'obstacles nettoyés")
plt.xlabel('Temps')

plt.suptitle('Agent de base')
plt.show()
